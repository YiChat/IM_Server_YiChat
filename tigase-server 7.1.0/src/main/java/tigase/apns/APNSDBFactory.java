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
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.conf.ConfigurationException;
import tigase.db.RepositoryFactory;

/**
 * @author bmalkow
 * 
 */
public class APNSDBFactory {

	public static final String DB_CLASS_KEY = "apns-token-db";

	public static final String DB_URI_KEY = "apns-token-db-uri";

	protected static final Logger log = Logger.getLogger(APNSDBFactory.class.getName());

	public static APNSDBProvider getAPNSDBManager(Map<String, Object> params) throws ConfigurationException {
		try {
			
			String uri = (String) params.get(DB_URI_KEY);
			String cl = (String) params.get(DB_CLASS_KEY);

			if (uri == null && cl == null)
				return null;

			if (log.isLoggable(Level.CONFIG))
				log.config("Using APNS Provider"
								+ "; params.size: " + params.size()
								+ "; uri: " + uri
								+ "; cl: " + cl);
			
			Class<? extends APNSDBProvider> cls = null;
			if (cl != null) {
				if (cl.contains("mysql")) {
					cls = MySqlAPNSProvider.class;
				} else{
					throw new ConfigurationException("Not found implementation of mysql Provider for " + uri);
				}
					
			}
			
			if (cls == null) {
				cls = RepositoryFactory.getRepoClass(APNSDBProvider.class, uri);
			}
			if (cls == null) {
				throw new ConfigurationException("Not found implementation of Roster DB Provider for " + uri);
			}
			
			APNSDBProvider provider = cls.newInstance();
			provider.initRepository(uri, null);
			return provider;		
		} catch (Exception e) {
			throw new ConfigurationException("Cannot initialize Roster APNS Provider", e);
		}
	}
}
