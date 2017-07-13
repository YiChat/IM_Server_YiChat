/*
 * Message.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
 */



package tigase.xmpp.impl;

//~--- non-JDK imports --------------------------------------------------------

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import tigase.apns.APNSManager;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPPacketFilterIfc;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.annotation.AnnotatedXMPPProcessor;
import tigase.xmpp.impl.annotation.Handle;
import tigase.xmpp.impl.annotation.Handles;
import tigase.xmpp.impl.annotation.Id;
import static tigase.xmpp.impl.MessagePush.*;

/**
 * Message forwarder class. Forwards <code>Message</code> packet to it's destination
 * address.
 *
 * Created: Tue Feb 21 15:49:08 2006
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
@Id(ID)
@Handles({
	@Handle(path={ ELEM_NAME },xmlns=XMLNS)
})
public class MessagePush
				extends AnnotatedXMPPProcessor
				implements XMPPProcessorIfc, XMPPPacketFilterIfc {

	protected static final String     ID = "message-push";
	
	protected static final String     ELEM_NAME = tigase.server.Message.ELEM_NAME;

	/** Class logger */
	private static final Logger   log    = Logger.getLogger(Message.class.getName());
	protected static final String   XMLNS  = "jabber:client";
	
	//~--- methods --------------------------------------------------------------

	@Override
	public void init(Map<String, Object> settings) throws TigaseDBException {
		super.init(settings);
	}

	@Override
	public void filter(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results) {
		C2SDeliveryErrorProcessor.filter(packet, session, repo, results, null);
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session,
			NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings)
					throws XMPPException {

		// For performance reasons it is better to do the check
		// before calling logging method.
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Processing packet: {0}, for session: {1}", new Object[] {
					packet,
					session });
		}

		// Message push via apns.. if the user is offline.
		if (session == null) {
			String type = packet.getElement().getAttributeStaticStr("type");
			if(type == null || !type.equals("chat")){
				return;
			}
			Element $body = packet.getElement().findChild(new String[]{"message", "body"});
			
			if($body == null)
				return;

			
			try {
				
				JSONObject obj = new JSONObject($body.getCData());
				if (obj.getJSONObject("data").get("chatType").equals("1") && obj.getJSONObject("data").get("msgType").equals(2001)){
					
					final String uid = obj.getJSONObject("data").get("to").toString();
					
					APNSManager.pushMsg(uid, 
							obj.getJSONObject("data").getJSONObject("ext").get("nick") + ":"
							+ obj.getJSONObject("data").getJSONObject("body").get("content"));
				}
			} catch (JSONException e) {
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "#APNS push error: json tanslation error!");
				}
			} catch (RuntimeException e) {
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "#APNS push error: " + e.getMessage());
				}
			}
			
		}
	}
}    

