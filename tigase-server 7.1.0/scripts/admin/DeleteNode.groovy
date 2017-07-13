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
 Delete PubSub node

 AS:Description: Delete node
 AS:CommandId: delete-node
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
import tigase.pubsub.modules.NodeDeleteModule.NodeDeleteHandler.NodeDeleteEvent;

def NODE = "node"

IPubSubRepository pubsubRepository = component.pubsubRepository

def p = (Packet)packet
def admins = (Set)adminsSet
def stanzaFromBare = p.getStanzaFrom().getBareJID()
def isServiceAdmin = admins.contains(stanzaFromBare)

def node = Command.getFieldValue(packet, NODE)

if (node == null) {
	def result = p.commandResult(Command.DataType.form);

	Command.addTitle(result, "Deleting a node")
	Command.addInstructions(result, "Fill out this form to delete a node.")

	Command.addFieldValue(result, NODE, node ?: "", "text-single",
			"The node to delete")	

	return result
}

def result = p.commandResult(Command.DataType.result)
try {
	if (isServiceAdmin) {
		def toJid = p.getStanzaTo().getBareJID();
		
		AbstractNodeConfig nodeConfig = pubsubRepository.getNodeConfig(toJid, node);

		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Node " + node + " cannot " + 
				"be deleted as it not exists yet.");
		}

		List<Packet> results = [];
		
		if (nodeConfig.isNotify_config()) {
			def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);
			Element del = new Element("delete");
			del.setAttribute("node", node);

			results.addAll(component.publishNodeModule.prepareNotification(del, p.getStanzaTo(), node,
							nodeConfig, nodeAffiliations, nodeSubscriptions));
		}		


		final String parentNodeName                 = nodeConfig.getCollection();
		CollectionNodeConfig parentCollectionConfig = null;
		
		if ((parentNodeName != null) &&!parentNodeName.equals("")) {
			parentCollectionConfig =
			(CollectionNodeConfig) pubsubRepository.getNodeConfig(toJid, parentNodeName);
			if (parentCollectionConfig != null) {
				parentCollectionConfig.removeChildren(node);
			}
		} else {
			pubsubRepository.removeFromRootCollection(toJid, node);
		}
		if (nodeConfig instanceof CollectionNodeConfig) {
			CollectionNodeConfig cnc     = (CollectionNodeConfig) nodeConfig;
			final String[] childrenNodes = cnc.getChildren();
			
			if ((childrenNodes != null) && (childrenNodes.length > 0)) {
				for (String childNodeName : childrenNodes) {
					AbstractNodeConfig childNodeConfig = pubsubRepository.getNodeConfig(toJid, childNodeName);
					
					if (childNodeConfig != null) {
						childNodeConfig.setCollection(parentNodeName);
						pubsubRepository.update(toJid, childNodeName, childNodeConfig);
					}
					if (parentCollectionConfig != null) {
						parentCollectionConfig.addChildren(childNodeName);
					} else {
						pubsubRepository.addToRootCollection(toJid, childNodeName);
					}
				}
			}
		}
		if (parentCollectionConfig != null) {
			pubsubRepository.update(toJid, parentNodeName, parentCollectionConfig);
		}
		
		pubsubRepository.deleteNode(toJid, node);
			
		NodeDeleteEvent event = new NodeDeleteEvent(packet, node);
		component.getEventBus().fire(event);
		
		results.each { packet ->
			component.addOutPacket(packet);
		}
		
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
	Command.addTextField(result, "Note", "Problem accessing database, node not deleted.");
}

return result
