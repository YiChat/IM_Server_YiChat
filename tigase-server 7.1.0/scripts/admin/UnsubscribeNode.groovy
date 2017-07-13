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
 Unsubscribe from PubSub node

 AS:Description: Unsubscribe fromo node
 AS:CommandId: unsubscribe-node
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

	Command.addTitle(result, "Unsubscribe from a node")
	Command.addInstructions(result, "Fill out this form to unsubscribe from a node.")

	Command.addFieldValue(result, NODE, node ?: "", "text-single",
			"The node to unsubscribe from")	
	Command.addFieldValue(result, JIDS, jids ?: "", "jid-multi",
			"JIDs to unsubscribe")
	
	return result
}

def result = p.commandResult(Command.DataType.result)
try {
	if (isServiceAdmin) {
		def toJid = p.getStanzaTo().getBareJID();
		
		AbstractNodeConfig nodeConfig = pubsubRepository.getNodeConfig(toJid, node);

		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Node " + node + " needs " + 
				"to be created before anyone will be able to subscribe or unsubscribe.");
		}
		
		def nodeAffiliations  = pubsubRepository.getNodeAffiliations(toJid, node);
		def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);
		
		jids.each { jidStr ->
			def jid = BareJID.bareJIDInstance(jidStr);
			def subscription = nodeSubscriptions.getSubscription(jid)
			if (!subscription || subscription == Subscription.none)
				return;
				
			nodeSubscriptions.changeSubscription(jid, Subscription.none);
			
			if (nodeSubscriptions.isChanged()) {
				pubsubRepository.update(toJid, node, nodeSubscriptions);
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
	Command.addTextField(result, "Note", "Problem accessing database, not unsubscribed from node.");
}

return result
