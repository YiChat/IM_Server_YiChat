package tigase.receipt;

import tigase.db.NonAuthUserRepository;
import tigase.receipt.BufferedMessageReceiptsStore.Callback;
import tigase.server.Packet;
import tigase.xmpp.JID;
import tigase.xmpp.XMPPResourceConnection;


public interface MessageReceiptsStore {
	
    void append(Packet message, XMPPResourceConnection session, NonAuthUserRepository userRepo);
    
    void remove(JID jid, String messageId);

	void setCallback(Callback callback);
}