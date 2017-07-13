/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
package tigase.apns;

import java.util.Map;

import tigase.db.Repository;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 * 
 */
public interface APNSDBProvider extends Repository {

	public void init(Map<String, Object> props);
	/**
	 * Adds join event.
	 * 
	 * @param room
	 * @param date
	 * @param senderJID
	 * @param nickName
	 */
	
	public void updateToken(BareJID bareJID, String $token);
	
	public void delToken(BareJID user);
	
	public Map<String, String> getAllTokens();
	


}
