/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */

/*
 Publish item to PubSub node

 AS:Description: Publish item to node
 AS:CommandId: publish-item
 AS:Component: pubsub
 */

package tigase.admin

import tigase.server.*
import tigase.util.*
import tigase.xmpp.*
import tigase.db.*
import tigase.xml.*
import tigase.vhosts.*
import tigase.pubsub.*
import tigase.pubsub.repository.IPubSubRepository
import tigase.pubsub.exceptions.PubSubException
import tigase.pubsub.exceptions.PubSubErrorCondition
import tigase.pubsub.modules.PublishItemModule.ItemPublishedHandler


try {
def NODE = "node"
def ID = "item-id";
def ENTRY = "entry";

IPubSubRepository pubsubRepository = component.pubsubRepository

def p = (Packet)packet
def admins = (Set)adminsSet
def stanzaFromBare = p.getStanzaFrom().getBareJID()
def isServiceAdmin = admins.contains(stanzaFromBare)

def node = Command.getFieldValue(packet, NODE)
def id = Command.getFieldValue(packet, ID);
def entry = Command.getFieldValues(packet, ENTRY);

if (!node || !entry) {
	def result = p.commandResult(Command.DataType.form);

	Command.addTitle(result, "Publish item to a node")
	Command.addInstructions(result, "Fill out this form to publish item to a node.")

	Command.addFieldValue(result, NODE, node ?: "", "text-single",
			"The node to publish to")
	Command.addFieldValue(result, ID, id ?: "", "text-single",
			"ID of item")
	Command.addFieldValue(result, ENTRY, entry ?: "", "text-multi",
			"Entry")

	return result
}

def result = p.commandResult(Command.DataType.result)
try {
	if (isServiceAdmin) {
		def toJid = p.getStanzaTo().getBareJID();

		def nodeConfig = pubsubRepository.getNodeConfig(toJid, node);
		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Node " + node + " needs " +
				"to be created before an item can be published.");
		}
		if (nodeConfig.getNodeType() == NodeType.collection) {
			throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
						new PubSubErrorCondition("unsupported", "publish"));
		}

		def nodeAffiliations = pubsubRepository.getNodeAffiliations(toJid, node);
		def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);

		Element item = new Element("item");
		if (!id) id = UUID.randomUUID().toString();
		item.setAttribute("id", id);

		def data = entry.join("\n")
		data = ((String) data).toCharArray();

		def handler = new DomBuilderHandler();
		def parser = SingletonFactory.getParserInstance();
		parser.parse(handler, data, 0, data.length);
		item.addChildren(handler.getParsedElements());

		def items = new Element("items");
		items.setAttribute("node", node);
		items.addChild(item);

		def results = [];
			component.publishNodeModule.sendNotifications(items, packet.getStanzaTo(),
			node, pubsubRepository.getNodeConfig(toJid, node),
			nodeAffiliations, nodeSubscriptions)

		def parents = component.publishNodeModule.getParents(toJid, node);

		if (!parents) {
			parents.each { collection ->
				def headers = [Collection:collection];

				AbstractNodeConfig colNodeConfig    = pubsubRepository.getNodeConfig(toJid, collection);
				def colNodeSubscriptions =
					pubsubRepository.getNodeSubscriptions(toJid, collection);
				def colNodeAffiliations =
					pubsubRepository.getNodeAffiliations(toJid, collection);

					component.publishNodeModule.sendNotifications(items, packet.getStanzaTo(),
									node, headers, colNodeConfig,
									colNodeAffiliations, colNodeSubscriptions)
			}
		}

		if (nodeConfig.isPersistItem()) {
			def nodeItems = pubsubRepository.getNodeItems(toJid, node);

			nodeItems.writeItem(System.currentTimeMillis(), id,
					p.getAttributeStaticStr("from"), item);

			if (nodeConfig.getMaxItems() != null) {
				component.publishNodeModule.trimItems(nodeItems, nodeConfig.getMaxItems());
			}
		}

		results.each { packet ->
			component.addOutPacket(packet);
		}

			component.getEventBus().fire(
				new ItemPublishedHandler.ItemPublishedEvent(packet.getStanzaTo().getBareJID(), node, itemsToSend));

		Command.addTextField(result, "Note", "Operation successful");
	} else {
		//Command.addTextField(result, "Error", "You do not have enough permissions to publish item to a node.");
		throw new PubSubException(Authorization.FORBIDDEN, "You do not have enough " +
				"permissions to publish item to a node.");
	}
} catch (PubSubException ex) {
	Command.addTextField(result, "Error", ex.getMessage())
	if (ex.getErrorCondition()) {
		def error = ex.getErrorCondition();
		Element errorEl = new Element("error");
		errorEl.setAttribute("type", error.getErrorType());
		Element conditionEl = new Element(error.getCondition(), ex.getMessage());
		conditionEl.setXMLNS(Packet.ERROR_NS);
		errorEl.addChild(conditionEl);
		Element pubsubCondition = ex.pubSubErrorCondition?.getElement();
		if (pubsubCondition)
			errorEl.addChild(pubsubCondition);
		result.getElement().addChild(errorEl);
	}
} catch (TigaseDBException ex) {
	Command.addTextField(result, "Note", "Problem accessing database, item not published to node.");
}

return result

} catch (Exception ex) {
	ex.printStackTrace();
}