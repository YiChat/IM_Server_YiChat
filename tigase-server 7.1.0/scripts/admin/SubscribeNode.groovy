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
 Subscribe to PubSub node

 AS:Description: Subscribe to node
 AS:CommandId: subscribe-node
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

def NODE = "node"
def JIDS = "jids";

IPubSubRepository pubsubRepository = component.pubsubRepository

def p = (Packet)packet
def admins = (Set)adminsSet
def stanzaFromBare = p.getStanzaFrom().getBareJID()
def isServiceAdmin = admins.contains(stanzaFromBare)

def node = Command.getFieldValue(packet, NODE)
def jids = Command.getFieldValues(packet, JIDS);

if (node == null) {
	def result = p.commandResult(Command.DataType.form);

	Command.addTitle(result, "Subscribe to a node")
	Command.addInstructions(result, "Fill out this form to subscribe to a node.")

	Command.addFieldValue(result, NODE, node ?: "", "text-single",
			"The node to subscribe to")	
	Command.addFieldValue(result, JIDS, jids ?: "", "jid-multi",
			"JIDs to subscribe")
	
	return result
}

def result = p.commandResult(Command.DataType.result)
try {
	if (isServiceAdmin) {
		def toJid = p.getStanzaTo().getBareJID();
		
		AbstractNodeConfig nodeConfig = pubsubRepository.getNodeConfig(toJid, node);

		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Node " + node + " needs " + 
				"to be created before anyone will be able to subscribe to a node.");
		}
		
		def nodeAffiliations  = pubsubRepository.getNodeAffiliations(toJid, node);
		def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);
		
		jids.each { jidStr ->
			def jid = BareJID.bareJIDInstance(jidStr);
			def subscription = nodeSubscriptions.getSubscription(jid)
			def affiliation = nodeAffiliations.getSubscriberAffiliation(jid).getAffiliation();
			
			affiliation = affiliation.getWeight() > Affiliation.member.getWeight() ? affiliation : Affiliation.member;
			String subid = nodeSubscriptions.getSubscriptionId(jid);
			
			if (subid == null) {
				subid = nodeSubscriptions.addSubscriberJid(jid, Subscription.subscribed);
				nodeAffiliations.addAffiliation(jid, affiliation);
			} else {
				nodeSubscriptions.changeSubscription(jid, Subscription.subscribed);
				nodeAffiliations.changeAffiliation(jid, affiliation);
			}

			// repository.setData(config.getServiceName(), nodeName, "owner",
			// JIDUtils.getNodeID(element.getAttribute("from")));
			if (nodeSubscriptions.isChanged()) {
				pubsubRepository.update(toJid, node, nodeSubscriptions);
			}
			if (nodeAffiliations.isChanged()) {
				pubsubRepository.update(toJid, node, nodeAffiliations);
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
	Command.addTextField(result, "Note", "Problem accessing database, not subscribed to node.");
}

return result
