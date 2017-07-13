package tigase.rest.users
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
 * Class implements ability to retrieve by service administrator list of registered accounts
 * Handles requests for /rest/users/
 *
 * Example format of content of response:
 * <users><items><item>user1@domain</item><item>user2@domain</item></items><count>2</count></users>
 */
class UsersHandler extends tigase.http.rest.Handler {

    public UsersHandler() {
		description = [
			regex : "/",
			GET : [ info:'Retrieve list of registered user jids', 
				description: """Request do not require any parameters and returns list of all registered user accounts on this server (for all vhosts).

Example response will look like this:
\${util.formatData([users:[items:['user1@example.com','user2@example.com','user1@example2.com'],count:3]])}
"""]
		];
        regex = /\//
        requiredRole = "admin"
        isAsync = false
        execGet = { Service service, callback, jid ->
            def users = service.getUserRepository().getUsers()
            callback([users:[items:users, count:users.size()]]);
        }
    }

}