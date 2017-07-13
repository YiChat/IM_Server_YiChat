package tigase.rest.user

/*
 * Tigase HTTP API
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
import tigase.http.rest.Service
import tigase.server.Iq
import tigase.server.Packet
import tigase.xml.Element
import tigase.xmpp.BareJID
import tigase.xmpp.StanzaType

/**
 * Class implements ability to manage users for service administrator
 * Handles requests for /rest/user/user@domain where user@domain is jid
 *
 * Example format of content of request or response:
 * <user><jid>user@domain</jid><password>Paa$$w0rd</password></jid></user>
 */
class UserAdminHandler extends tigase.http.rest.Handler {

	 def TIMEOUT = 30 * 1000;
	 def COMMAND_XMLNS = "http://jabber.org/protocol/commands";
	
    public UserAdminHandler() {
		description = [
			regex : "/{user_jid}",
			GET : [ info:'Retrieve user account details', 
				description: """Only required parameter is part of url {user_jid} which is jid of user which account informations you want to retrieve.
Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header\n\

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
""" ],
			PUT : [ info:'Create new user account',
				description: """Part of url {user_jid} is parameter which is jid of user which account you want to create, ie. user@example.com.
To create account additional data needs to be passed as content of HTTP request:
\${util.formatData([user:[password:'some_password',email:'user@example.com']])}

Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
""" ],
			POST : [ info:'Update user account',
				description: """Part of url {user_jid} is parameter which is jid of user which account you want to update, ie. user@example.com.\n\
Additional data needs to be passed as content of HTTP request to change password for this account:
\${util.formatData([user:[password:'some_password']])}
Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
"""],
			DELETE : [ info:'Delete user account',
				description: """Part of url {user_jid} is parameter which is jid of user which account you want to remove, ie. user@example.com.
Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
"""],
		];
        regex = /\/([^@\/]+)@([^@\/]+)/
		authRequired = { api_key -> return api_key == null && requiredRole != null }
        requiredRole = "admin"
        isAsync = true
        execGet = { Service service, callback, user, localPart, domain ->
            def jid = BareJID.bareJIDInstance(localPart, domain);
            def uid = service.getUserRepository().getUserUID(jid);
            if (uid <= 0) {
                callback(null);
            }
            else {
                callback([user:[jid:"$localPart@$domain", domain:domain, uid:uid]]);
            }
        }
        execPut = { Service service, callback, user, content, localPart, domain ->
            def jid = BareJID.bareJIDInstance(localPart, domain);
            def password = content.user.password;
			def email = content.user.email;
			try {
		        service.getAuthRepository().addUser(jid, password);
				def uid = service.getUserRepository().getUserUID(jid);
				if (uid && email) {
					service.getUserRepository().setData(jid, "email", email);
				}
				callback([user:[jid:"$localPart@$domain", domain:domain, uid: uid]]);
			} catch (tigase.db.UserExistsException ex) {
				callback({ req, resp -> 
						resp.sendError(409, "User exists");
					});
			}
        }
        execDelete = { Service service, callback, user, localPart, domain ->
            def jid = BareJID.bareJIDInstance(localPart, domain);
            def uid = service.getUserRepository().getUserUID(jid);
            
			Element iq = new Element("iq");
            iq.setAttribute("to", "sess-man@" + domain);
			if (user != null && user != "null") {
				iq.setAttribute("from", user.toString());
			}
            iq.setAttribute("type", "set");
            iq.setAttribute("id", UUID.randomUUID().toString())

            Element command = new Element("command");
            command.setXMLNS(COMMAND_XMLNS);
            command.setAttribute("node", "http://jabber.org/protocol/admin#delete-user");
            iq.addChild(command);

			Element x = new Element("x");
			x.setXMLNS("jabber:x:data");
			x.setAttribute("type", "submit");
			command.addChild(x);
			
			Element fieldEl = new Element("field");
			fieldEl.setAttribute("var", "notify-cluster");
			fieldEl.addChild(new Element("value", "true"));
			x.addChild(fieldEl);
			fieldEl = new Element("field");
			fieldEl.setAttribute("var", "accountjids");
			fieldEl.addChild(new Element("value", jid.toString()));
			x.addChild(fieldEl);
			
			service.sendPacket(new Iq(iq), TIMEOUT, { Packet result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }
				
				command = result.getElement().getChild("command", COMMAND_XMLNS);
				def noteEl = command.getChildren().find{ it.getName() == "x" }.getChildren().find{ it.getAttribute("var") == "Notes" };
				if (noteEl == null) {
					callback(null);
					return;
				}
				
				callback([user:[jid:"$localPart@$domain", domain:domain, uid:uid]]);
			});
        }
        execPost = { Service service, callback, user, content, localPart, domain ->
            def jid = BareJID.bareJIDInstance(localPart, domain);
            def password = content.user.password;
            service.getAuthRepository().updatePassword(jid, password)
            def uid = service.getUserRepository().getUserUID(jid);
            callback([user:[jid:"$localPart@$domain", domain:domain, uid: uid]]);
        }
    }

}