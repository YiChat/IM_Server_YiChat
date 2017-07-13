/*
 * MessageAmp.java
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

import static tigase.server.amp.AmpFeatureIfc.MSG_OFFLINE_PROP_KEY;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import tigase.apns.APNSManager;
import tigase.db.MsgRepositoryIfc;
import tigase.db.NonAuthUserRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.receipt.BufferedMessageReceiptsStore;
import tigase.receipt.BufferedMessageReceiptsStore.Callback;
import tigase.receipt.MessageReceiptsStore;
import tigase.server.Packet;
import tigase.server.amp.AmpFeatureIfc;
import tigase.server.amp.MsgRepository;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;

/**
 * Created: Apr 29, 2010 5:00:25 PM
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class MessageReceipts
				extends XMPPProcessor
				implements XMPPProcessorIfc , Callback{
	private static final String     RECEIPT_JID_PROP_KEY     = "receipt-jid";
	public static final String     RECEIPT_RECEIVED     = "received";
	public static final String     RECEIPT_REQUEST     = "request";
	public static final String     DELAY     = "delay";
	private static String 			host = null;
	
	private static final String[][] ELEMENTS             = {
		{ Message.ELEM_NAME , RECEIPT_RECEIVED}, { Message.ELEM_NAME , RECEIPT_REQUEST}
	};
	private static final String     XMLNS                = "urn:xmpp:receipts";
	private static final String     ID                   = XMLNS;
	private static final Logger     log = Logger.getLogger(MessageReceipts.class.getName());
	private static final String[]   XMLNSS = { XMLNS, XMLNS };
	private static Element[]        DISCO_FEATURES = { new Element("feature",
			new String[] { "var" }, new String[] { XMLNS }),
			new Element("feature", new String[] { "var" }, new String[] { "msgoffline" }) };
	private static final String defHost = DNSResolver.getDefaultHostname();

	//~--- fields ---------------------------------------------------------------

	private JID             receiptJID           = null;
	private MsgRepositoryIfc   msg_repo         = null;
	private OfflineMessages offlineProcessor = new OfflineMessages();
	private Message         messageProcessor = new Message();

	//~--- methods --------------------------------------------------------------

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void init(Map<String, Object> settings) throws TigaseDBException {
		super.init(settings);

		String receiptJIDstr = (String) settings.get(RECEIPT_JID_PROP_KEY);

		if (null != receiptJIDstr) {
			receiptJID = JID.jidInstanceNS(receiptJIDstr);
		} else {
			receiptJID = JID.jidInstanceNS("receipt@" + defHost);
		}
		log.log(Level.CONFIG, "Loaded RECEIPT_JID option: {0} = {1}", new Object[] {
				RECEIPT_JID_PROP_KEY,
				receiptJID });

		String off_val = (String) settings.get(MSG_OFFLINE_PROP_KEY);

		if (off_val == null) {
			off_val = System.getProperty(MSG_OFFLINE_PROP_KEY);
		}
		if ((off_val != null) &&!Boolean.parseBoolean(off_val)) {
			log.log(Level.CONFIG, "Offline messages storage: {0}", new Object[] { off_val });
			offlineProcessor = null;
			DISCO_FEATURES = new Element[] { new Element("feature", new String[] { "var" },
					new String[] { XMLNS }) };
		}

		String msg_repo_uri = (String) settings.get(AmpFeatureIfc.AMP_MSG_REPO_URI_PROP_KEY);
		String msg_repo_cls = (String) settings.get(AmpFeatureIfc.AMP_MSG_REPO_CLASS_PROP_KEY);
		
		if (msg_repo_uri == null) {
			msg_repo_uri = System.getProperty(AmpFeatureIfc.AMP_MSG_REPO_URI_PROP_KEY);
			if (msg_repo_uri == null) {
				msg_repo_uri = System.getProperty(RepositoryFactory.GEN_USER_DB_URI_PROP_KEY);
			}
		}
		if (msg_repo_cls == null) {
			msg_repo_cls = System.getProperty(AmpFeatureIfc.AMP_MSG_REPO_CLASS_PROP_KEY);
		}
		if (msg_repo_uri != null) {
			Map<String, String> db_props = new HashMap<String, String>(4);

			for (Map.Entry<String, Object> entry : settings.entrySet()) {
				db_props.put(entry.getKey(), entry.getValue().toString());
			}

			// Initialization of repository can be done here and in Store
			// class so repository related parameters for JDBCMsgRepository
			// should be specified for AMP plugin and AMP component
			try {
				msg_repo = MsgRepository.getInstance(msg_repo_cls, msg_repo_uri);
				msg_repo.initRepository(msg_repo_uri, db_props);
			} catch (TigaseDBException ex) {
				msg_repo = null;
				log.log(Level.WARNING, "Problem initializing connection to DB: ", ex);
			}
		}
	}
	
	@Override
	public void process(Packet packet, XMPPResourceConnection session,
			NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings)
					throws XMPPException {
	
		if(session == null){
			return;
		}
		
		if(packet.getElement().getChild(RECEIPT_REQUEST) != null){
			
			BareJID to = (packet.getStanzaTo() != null) ? packet.getStanzaTo().getBareJID() : null;
			
			// send receipt back if the receiver is offline.
		    // end of if (session == null)
			if (session.isUserId(to)) {
				
				if (log.isLoggable(Level.FINEST)) {
	                log.log(Level.FINEST, "Process packet: {0}", packet);
				}
				
				store(packet.copyElementOnly(), session, repo);
				return;
			}
			
			BareJID from = (packet.getStanzaFrom() != null) ? packet.getStanzaFrom().getBareJID() : null;
			
			if (session != null && session.isUserId(from)) {
				final Packet pac = packet.copyElementOnly();
				Packet receipt = makeReceipts(pac);
				results.offer(receipt);
				
				if (log.isLoggable(Level.FINEST)) {
	                log.log(Level.FINEST, "Send receipt packet: {0}", packet);
				}
				
				return;
			}
			
		} else {
			 // Remember to cut the resource part off before comparing JIDs
	 		BareJID id = (packet.getStanzaTo() != null) ? packet.getStanzaTo().getBareJID() : null;

	 		// Checking if this is a receipt packet to server
	 		if (!session.isServerSession() && id.toString().equals(session.getBareJID().getDomain())) {
	 			
	 			if (log.isLoggable(Level.FINEST)) {
	 				log.log(Level.FINEST, "Received receipts: {0}", packet);
	 			}
	 			
	 			final String packetId = packet.getElement().getChild(RECEIPT_RECEIVED).getAttributeStaticStr("id");
	 			remove(packet.getStanzaFrom(), packetId);
	 		}
	 		
		}
	}
	
	private Packet makeReceipts(Packet packet) {
		
		Packet receipt = null;
		final JID fromJID =   packet.getStanzaFrom();
		Element elm = new Element(Message.ELEM_NAME, new String[] { "to", "from", "type", "xmlns"},
								new String[]{ fromJID.toString(), fromJID.getDomain(), "chat", "jabber:client"});
		
		elm.addChild(new Element(RECEIPT_RECEIVED, 
							new String[] { "id", "xmlns" }, 
							new String[] {packet.getStanzaId(), XMLNS}));
		
		try {
			receipt = Packet.packetInstance(elm);
		} catch (TigaseStringprepException e) {
			e.printStackTrace();
		}
		
		return receipt;
	}
	
	protected void store(Packet packet, XMPPResourceConnection session, NonAuthUserRepository userRepo) {
		MessageReceiptsStore store = BufferedMessageReceiptsStore.getInstance();
		if (null != store) {
			store.setCallback(this);
			store.append(packet, session, userRepo);
		}
	}
	
	private void remove(JID stanzaFrom, String packetId) {
		MessageReceiptsStore store = BufferedMessageReceiptsStore.getInstance();
		if (null != store) {
			store.remove(stanzaFrom, packetId);
		}
	}
	

	@Override
	public void messageExpired(Packet message, long arrivedTime, XMPPResourceConnection session, NonAuthUserRepository userRepo) {
		try {
			offlineProcessor.savePacketForOffLineUser(message, msg_repo, userRepo);
			System.out.println("----------------Expired message store--------" + message.getStanzaId() + "-------------");
			if(session != null ){
				session.logout();
			}
			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "#Receipt : Expired message store into DB error, close session : packet: {0}", message);
			}
		} catch (UserNotFoundException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Expired message store into DB error, packet: {0}", message);
			}
			e.printStackTrace();
		} catch (NotAuthorizedException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Expired message, Close session error, packet: {0}", message);
			}
		}
		
		String type = message.getElement().getAttributeStaticStr("type");
		if(type == null || !type.equals("chat")){
			return;
		}
		Element $body = message.getElement().findChild(new String[]{"message", "body"});
		
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

	@Override
	public void messageDropped(JID jid, String messageId,
			boolean hasBuffered) {
		// TODO Auto-generated method stub
		
	}

		

	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		return DISCO_FEATURES;
	}

	@Override
	public String[][] supElementNamePaths() {
		return ELEMENTS;
	}

	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}

}
