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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.db.RepositoryFactory;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 * 
 */
@Repository.Meta( supportedUris = { "jdbc:mysql:.*" } )
public class MySqlAPNSProvider implements APNSDBProvider {
	
	// add by cai 
	
	private static final String DEF_TOKEN_TABLE = "tig_users";
	
	public static final String UPDATE_TOKEN_KEY = "UPDATE_TOKEN_KEY";

	public static final String GET_ALL_TOKENS_KEY = "GET_ALL_TOKENS_KEY";
	
	
	public static final String UPDATE_TOKEN_VAL = "update " + DEF_TOKEN_TABLE + " set push_token = ? where user_id = ?";

	public static final String GET_ALL_TOKENS_VAL = "select user_id, push_token from " + DEF_TOKEN_TABLE + " where push_token is not null";

	private Logger log = Logger.getLogger(this.getClass().getName());

	protected DataRepository dataRepository;
	
	/**
	 * @param dataRepository
	 */
	public MySqlAPNSProvider() {
	}
	
	@Override
	public void initRepository(String resource_uri, Map<String, String> params) throws DBInitException {
		try {
			dataRepository = RepositoryFactory.getDataRepository(null, resource_uri, params);
		} catch (Exception ex) {
			throw new DBInitException("Error during initialization of ext repository", ex);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void init(Map<String, Object> props) {
		try {
			internalInit();
		} catch (SQLException e) {
			e.printStackTrace();
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Initializing problem", e);
			try {
				if (log.isLoggable(Level.INFO))

				internalInit();
			} catch (SQLException e1) {
				if (log.isLoggable(Level.WARNING))
					log.log(Level.WARNING, "Can't access youshixiu db", e1);
				throw new RuntimeException(e1);
			}
		}
	}

	private void internalInit() throws SQLException {
		this.dataRepository.initPreparedStatement(UPDATE_TOKEN_KEY, UPDATE_TOKEN_VAL);
		this.dataRepository.initPreparedStatement(GET_ALL_TOKENS_KEY, GET_ALL_TOKENS_VAL);
	}
	
	@Override
	public void delToken(BareJID user) {
		PreparedStatement st = null;
		try {
			st = this.dataRepository.getPreparedStatement(user, UPDATE_TOKEN_KEY);
			final int uid = Integer.valueOf(user.getLocalpart());
			
			synchronized (st) {
				st.setNull(1, Types.VARCHAR);
				st.setString(2, user.toString());
				st.executeUpdate();
			}
		} catch (SQLException e) {
			log.log(Level.WARNING, "Can't del token from " + DEF_TOKEN_TABLE, e);
			throw new RuntimeException(e);
		} finally {
			dataRepository.release(null, null);
		}
		
	}

	@Override
	public void updateToken(BareJID bareJID, String token) {
		
		if(token == null)
			return;
		
		PreparedStatement st = null;
		try {
			st = this.dataRepository.getPreparedStatement(bareJID, UPDATE_TOKEN_KEY);
			
			synchronized (st) {
				st.setString(1, token);
				st.setString(2, bareJID.toString());
				st.executeUpdate();
			}
		} catch (SQLException e) {
			log.log(Level.WARNING, "Can't update push_token from " + DEF_TOKEN_TABLE, e);
			throw new RuntimeException(e);
		} finally {
			dataRepository.release(null, null);
		}
		
	}
	

	@Override
	public Map<String, String> getAllTokens() {
		PreparedStatement st = null;
		ResultSet rs = null;
		
		try {
			Map<String, String> tokens = new ConcurrentHashMap<String, String>();
			
			st = this.dataRepository.getPreparedStatement(null, GET_ALL_TOKENS_KEY);
			
			synchronized (st) {
				rs = st.executeQuery();
				while(rs.next()){
					tokens.put(rs.getString("user_id").split("@")[0], rs.getString("push_token"));
				}
			}
			return tokens;
		} catch (SQLException e) {
			log.log(Level.WARNING, "Can't get all tokens from " + DEF_TOKEN_TABLE, e);
			throw new RuntimeException(e);
		} finally {
			dataRepository.release(null, rs);
		}
	}

}
