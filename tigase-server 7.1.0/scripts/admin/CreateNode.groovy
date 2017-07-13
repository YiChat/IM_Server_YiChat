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
 Create PubSub node

 AS:Description: Create node
 AS:CommandId: create-node
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
import tigase.pubsub.modules.NodeCreateModule.NodeCreateHandler.NodeCreateEvent;

def NODE = "node"
def OWNER = "owner";

IPubSubRepository pubsubRepository = component.pubsubRepository

def p = (Packet)packet
def admins = (Set)adminsSet
def stanzaFromBare = p.getStanzaFrom().getBareJID()
def isServiceAdmin = admins.contains(stanzaFromBare)

def node = Command.getFieldValue(packet, NODE)
def owner = Command.getFieldValue(packet, OWNER);

if (node == null) {
	def result = p.commandResult(Command.DataType.form);

	Command.addTitle(result, "Creating a node")
	Command.addInstructions(result, "Fill out this form to create a node.")

	Command.addFieldValue(result, NODE, node ?: "", "text-single",
			"The node to create")	
	Command.addFieldValue(result, OWNER, owner ?: "", "jid-single",
			"Owner JID")
	
	def nodeConfig = new LeafNodeConfig(null);
	List<Element> fields = nodeConfig.getFormElement().getChildren();
	Element x = Command.getData(result, "x","jabber:x:data");
	x.addChildren(fields);
	//Command.addFieldValue(result, "FORM_TYPE", "http://jabber.org/protocol/admin",
	//		"hidden")

	return result
}

def result = p.commandResult(Command.DataType.result)
try {
	if (isServiceAdmin) {
		def toJid = p.getStanzaTo().getBareJID();

		def nodeConfig = pubsubRepository.getNodeConfig(toJid, node);

		if (nodeConfig != null) {
			throw new PubSubException(Authorization.CONFLICT, "Node " + node + " already exists");
		}
		
		def nodeTypeStr = Command.getFieldValue(p, "pubsub#node_type");
		def nodeType = nodeTypeStr ? NodeType.valueOf(nodeTypeStr) : NodeType.leaf;
		nodeConfig = (nodeType == NodeType.leaf) ? new LeafNodeConfig(node, component.defaultNodeConfig) : new CollectionNodeConfig(node);
		
		Command.getData(p, "x", "jabber:x:data").getChildren().each { fieldEl ->
			def var = fieldEl.getAttribute("var");			
			def field = nodeConfig.getForm().get(var);
			def value = fieldEl.getChildCData( "/field/value")
			if (!field) return;
			if (field.getType().name().endsWith("-multi")) {
				nodeConfig.setValues(field.getVar(), value.tokenize());
			}
			else {
				nodeConfig.setValue(field.getVar(), value);
			}
		};
		
		def collection = nodeConfig.getCollection();
		CollectionNodeConfig colNodeConfig = null;
		
		if (collection != '') {
			AbstractNodeConfig absNodeConfig = pubsubRepository.getNodeConfig(toJid, collection);

			if (absNodeConfig == null) {
				throw new PubSubException(p.getElement(), Authorization.ITEM_NOT_FOUND);
			} else if (absNodeConfig.getNodeType() == NodeType.leaf) {
				throw new PubSubException(p.getElement(), Authorization.NOT_ALLOWED);
			}
			colNodeConfig = (CollectionNodeConfig) absNodeConfig;
		}		
			
		if (owner == null || owner.isEmpty()) { 
			owner = p.getStanzaFrom().getBareJID();
		} else {
			owner = BareJID.bareJIDInstance(owner);
		}
		pubsubRepository.createNode(toJid, node, owner,
			nodeConfig, nodeType, collection);
		
		def nodeaAffiliations  = pubsubRepository.getNodeAffiliations(toJid, node);

		nodeaAffiliations.addAffiliation(owner,Affiliation.owner);
		pubsubRepository.update(toJid, node, nodeaAffiliations);
		if (colNodeConfig == null) {
			pubsubRepository.addToRootCollection(toJid, node);
		} else {
			colNodeConfig.addChildren(node);
			pubsubRepository.update(toJid, collection, colNodeConfig);
		}
		
		NodeCreateEvent event = new NodeCreateEvent(packet, node);		
		component.getEventBus().fire(event);

		if (collection != '') {
			def colNodeSubscriptions =
				pubsubRepository.getNodeSubscriptions(toJid, collection);
			def colNodeAffiliations =
				pubsubRepository.getNodeAffiliations(toJid, collection);
			Element colE = new Element("collection");
			colE.setAttribute("node", collection);
			Element associateEl = new Element("associate");
			associateEl.setAttribute("node", node);
			colE.addChild(associateEl);
			def results = (component.publishNodeModule.sendNotifications(colE,
							packet.getStanzaTo(), collection, nodeConfig,
							colNodeAffiliations, colNodeSubscriptions));
			results.each { packet -> 
				component.addOutPacket(packet);
			}
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
	Command.addTextField(result, "Note", "Problem accessing database, node not created.");
}

return result
