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
import tigase.xmpp.BareJID

/**
 * Class implements ability to change user password or remove account by user
 * Handles requests for /rest/user/ and executes request for currently authenticated user
 *
 * Example format of content of request or response:
 * <user><jid>user@domain</jid><password>Paa$$w0rd</password></jid></user>
 */
class UserUserHandler extends tigase.http.rest.Handler {

    public UserUserHandler() {
		description = [
			regex : "/",
			GET : [ info:'Retrieve details of active user account', 
				description: """Only required parameter is part of url {user_jid} which is jid of user which account informations you want to retrieve.
Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header\n\

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
""" ],
			POST : [ info:'Change password of active user account',
				description: """Part of url {user_jid} is parameter which is jid of user which account you want to update, ie. user@example.com.\n\
Additional data needs to be passed as content of HTTP request to change password for this account:
\${util.formatData([user:[password:'some_password']])}
Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
"""],
			DELETE : [ info:'Delete active user account',
				description: """Part of url {user_jid} is parameter which is jid of user which account you want to remove, ie. user@example.com.
Data will be returned in form of JSON or XML depending on selected format by Accept HTTP header

Example response:
\${util.formatData([user:[jid:'user@example.com', domain:'example.com', uid:10 ]])}				
"""],
		];
        regex = /\//
        requiredRole = "user"
        isAsync = false
        execGet = { Service service, callback, jid ->
            def uid = service.getUserRepository().getUserUID(jid);
            if (uid <= 0) {
                callback(null);
            }
            else {
                callback([user:[jid:"${jid.toString()}", domain:jid.getDomain(), uid:uid]]);
            }
        }
        execDelete = { Service service, callback, jid ->
			def uid = service.getUserRepository().getUserUID(jid);
				service.getAuthRepository().removeUser(jid)
				try {
					service.getUserRepository().removeUser(bareJID)
				} catch (tigase.db.UserNotFoundException ex) {
				
				}
            callback([user:[jid:"${jid.toString()}", domain:jid.getDomain(), uid:uid]]);
        }
        execPost = { Service service, callback, content, jid ->
            def password = content.user.password;
            service.getAuthRepository().updatePassword(jid, password)
            def uid = service.getUserRepository().getUserUID(jid);
            callback([user:[jid:"${jid.toString()}", domain:jid.getDomain(), uid:uid]]);
        }
    }

}