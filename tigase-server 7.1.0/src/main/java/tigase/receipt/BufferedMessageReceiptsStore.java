package tigase.receipt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import tigase.db.NonAuthUserRepository;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.ThreadPoolUtils;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPResourceConnection;



public class BufferedMessageReceiptsStore implements MessageReceiptsStore {

    private static final Logger LOG = Logger.getLogger(BufferedMessageReceiptsStore.class.getName());

    private Map<String, UserMessageCollection> messages = new ConcurrentHashMap<String, UserMessageCollection>();
    private ScheduledThreadPoolExecutor executorService = ThreadPoolUtils.newScheduledPool("BufferedMessageStorePool",
            "BufferedMessageStorePoolSize");
    
    private static BufferedMessageReceiptsStore instance = null;
    
    private long holdTime = 2500L;
    private Callback callback = null;

    public BufferedMessageReceiptsStore(long holdTime) {
        this.holdTime = holdTime;
    }

    @Override
    public void setCallback(Callback callback) {
        if (null == this.callback) {
            this.callback = callback;
        }
    }
    
    public static BufferedMessageReceiptsStore getInstance() {
    	if (instance == null){
    		instance = new BufferedMessageReceiptsStore(5000);
    	}
		return instance;
    }
    

    /*
     * (non-Javadoc)
     * @see com.suning.openfire.MessageReceiptsStore#append(org.xmpp.packet.Message)
     */
    @Override
    public void append(Packet packet, XMPPResourceConnection session, NonAuthUserRepository userRepo) {
        this.store(packet.getStanzaTo(), packet, session, userRepo);
    }

    /*
     * (non-Javadoc)
     * @see com.suning.openfire.MessageReceiptsStore#remove(org.xmpp.packet.JID, java.lang.String)
     */
    @Override
    public void remove(JID jid, String messageId) {
        this.drop(jid, messageId);
    }

    public Lock getLock(String barejid) {
        UserMessageCollection collection = this.messages.get(barejid);
        return null == collection ? null : collection.getLock();
    }

    /**
     * 功能描述: <br>
     * 〈功能详细描述〉
     * 
     * @param collection
     * @see [相关类/方法](可选)
     * @since [产品/模块版本](可选)
     */
    private void removeIfEmpty(UserMessageCollection collection) {
        if (collection.isEmpty()) {
            synchronized (this.messages) {
            	this.messages.remove(collection.getBareJid());
            }
        }
    }

    public void store(JID jid, Packet message, XMPPResourceConnection session, NonAuthUserRepository userRepo) {
    	final String id = message.getStanzaId();
        if (StringUtils.isNotBlank(id)) {
        	if(message.getElement().getAttributeStaticStr("type").equals(StanzaType.groupchat))
        		return;
        	
        	if(message.getElement().getChild("body") == null)
    			return;

        	// add by cai 
			// add stamp
			if(message.getElement().getChild("stamp") == null)
				message.getElement().addChild(new Element("stamp", System.currentTimeMillis() + ""));
        	
        	final String bareJid = jid.getBareJID().toString();
        	
            UserMessageCollection collection = this.messages.get(bareJid);
            if (null == collection) {
                synchronized (this.messages) {
                    if (null == collection) {
                        collection = new UserMessageCollection(bareJid, session, userRepo);
                        this.messages.put(bareJid, collection);
                    }
                }
            }
            
            final BufferedMessage buffered = new BufferedMessage(message, this.holdTime, collection.getLock());
            
            BufferedMessage old = collection.remove(id);
            if( old != null){
            	old.drop();
            }
            collection.add(buffered);
            
            System.out.println("----------save---------" + buffered.getMessageId());

            this.executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        buffered.getLock().lock();
                        UserMessageCollection collection = messages.get(buffered.getTo().getBareJID().toString());
                        if(collection != null){
                        	collection.remove(buffered);
                        	removeIfEmpty(collection);
                        }
                        if (buffered.isDroped()) {
                        	System.out.println("----------Droped---------" + buffered.getMessageId());
                        } else {
                        	callback.messageExpired(buffered.message, buffered.arrivedTiem, collection.getSession(), collection.getUserRepo());
                        }
                        
                    } catch (Exception ex) {
                    	ex.printStackTrace();
//                        LOG.warning("BufferedMessageReceiptsStore MessageID: "
//                                + buffered.message.getStanzaId()
//                                + " " + ex.getMessage());
                    } finally {
                        buffered.getLock().unlock();
                    }
                }
            }, this.holdTime, TimeUnit.MILLISECONDS);
        }
    }

    public void drop(JID jid, String messageId) {
        if (null != jid && StringUtils.isNotBlank(messageId)) {
            UserMessageCollection collection = this.messages.get(jid.getBareJID().toString());
            if (null != collection) {
                try {
                    collection.getLock().lock();
                    BufferedMessage buffered = collection.remove(messageId);
                    if (null != buffered) {
                        //缓存队列中删除
                        buffered.drop();
                        System.out.println("----------droping--------" + buffered.getMessageId());
//                        this.callback.messageDropped(jid, messageId, true);
                        return;
                    }
                } finally {
                    collection.getLock().unlock();
                }
            }
            
            if(this.callback != null)
            	this.callback.messageDropped(jid, messageId, false);
        }
    }

//    private UserMessageCollection getUserMessageCollection(String bareJid) {
//        UserMessageCollection collection = this.messages.get(bareJid);
//        if (null == collection) {
//            synchronized (this.messages) {
//                if (null == collection) {
//                    collection = new UserMessageCollection(bareJid, session);
//                    this.messages.put(bareJid, collection);
//                }
//            }
//        }
//        return collection;
//    }

//    public Collection<OfflineMessage> getStoredMessages(JID jid) {
//        List<OfflineMessage> messages = new ArrayList<OfflineMessage>();
//        UserMessageCollection collection = this.messages.get(jid.toBareJID());
//        if (null != collection) {
//            for (BufferedMessage buffered : collection.getBufferedMessages()) {
//                if (!buffered.isDroped()) {
//                    messages.add(new OfflineMessage(new Date(buffered.arrivedTiem), buffered.message.getElement()
//                            .createCopy()));
//                }
//            }
//        }
//        return messages;
//    }

    private class UserMessageCollection {
    	
    	private XMPPResourceConnection session;
    	
    	private NonAuthUserRepository userRepo;

        private String bareJid = null;
        private Lock lock = new ReentrantLock();
        private Map<String, BufferedMessage> messages = new ConcurrentHashMap<String, BufferedMessage>();

        /**
         * @param bareJid
         */
        private UserMessageCollection(String bareJid, XMPPResourceConnection session, NonAuthUserRepository userRepo) {
            this.session = session;
            this.bareJid = bareJid;
            this.userRepo = userRepo;
        }

        /**
         * 功能描述: <br>
         * 〈功能详细描述〉
         * 
         * @return
         * @see [相关类/方法](可选)
         * @since [产品/模块版本](可选)
         */
        private Lock getLock() {
            return lock;
        }

        /**
         * @return the bareJid
         */
        private String getBareJid() {
            return bareJid;
        }

		private XMPPResourceConnection getSession() {
        	return this.session;
        }
		
		public NonAuthUserRepository getUserRepo() {
			return userRepo;
		}

        /**
         * 功能描述: <br>
         * 〈功能详细描述〉
         * 
         * @param messageId
         * @see [相关类/方法](可选)
         * @since [产品/模块版本](可选)
         */
        public BufferedMessage remove(String messageId) {
            return this.messages.remove(messageId);
        }

        private void add(BufferedMessage message) {
            this.messages.put(message.getMessageId(), message);
        }

        private boolean isEmpty() {
            return this.messages.isEmpty();
        }

        private boolean remove(BufferedMessage message) {
            return null != this.messages.remove(message.getMessageId());
        }

        private Collection<BufferedMessage> getBufferedMessages() {
            Collection<BufferedMessage> messages = this.messages.values();
            List<BufferedMessage> list = new ArrayList<BufferedMessage>(messages);
            Collections.sort(list);
            return list;
        }
    }

    private class BufferedMessage implements Delayed {
        private long arrivedTiem = 0;
        private Message message = null;
        private long holdTime = 0;
        private boolean droped = false;
        private Lock lock = null;

        private BufferedMessage(Packet packet, long holdTime, Lock lock) {
            try {
				this.message = new Message(packet.copyElementOnly().getElement());
	            this.arrivedTiem = System.currentTimeMillis();
	            this.holdTime = holdTime;
	            this.lock = lock;
            } catch (TigaseStringprepException e) {
            	e.printStackTrace();
            }
        }

        private String getMessageId() {
            return this.message.getStanzaId();
        }

        /**
         * @return the lock
         */
        private Lock getLock() {
            return lock;
        }

        /*
         * (non-Javadoc)
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(Delayed o) {
            return (int) (this.arrivedTiem - ((BufferedMessage) o).arrivedTiem);
        }

        /*
         * (non-Javadoc)
         * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
         */
        @Override
        public long getDelay(TimeUnit unit) {
            return (this.arrivedTiem + this.holdTime) - System.currentTimeMillis();
        }

        private void drop() {
            this.droped = true;
        }

        private boolean isDroped() {
            return this.droped;
        }

        private JID getTo() {
            return this.message.getStanzaTo();
        }
    }

    /**
     * 〈一句话功能简述〉<br>
     * 〈功能详细描述〉
     * 
     * @author Administrator
     * @see [相关类/方法]（可选）
     * @since [产品/模块版本] （可选）
     */
    public interface Callback {

        /**
         * 功能描述: <br>
         * 〈功能详细描述〉
         * 
         * @param message
         * @param arrivedTime
         * @see [相关类/方法](可选)
         * @since [产品/模块版本](可选)
         */
        void messageExpired(Packet message, long arrivedTime, XMPPResourceConnection session, NonAuthUserRepository userRepo);

        /**
         * 功能描述: <br>
         * 〈功能详细描述〉
         * 
         * @param jid
         * @param messageId
         * @see [相关类/方法](可选)
         * @since [产品/模块版本](可选)
         */
        void messageDropped(JID jid, String messageId, boolean hasBuffered);

    }
    
    public void stop(){
//        for(UserMessageCollection collection :messages.values()){
//            Collection<BufferedMessage>  bufferedMsgs=  collection.getBufferedMessages();
//            for(BufferedMessage msg: bufferedMsgs){
//                callback.messageExpired(msg.message, msg.arrivedTiem, null, null);
//            }
//        }
    }
    
    public List<Message> getAllReceiptsBufferedMsgs(){
        List<Message> list = new ArrayList<Message>();
        for(UserMessageCollection collection :messages.values()){
            Collection<BufferedMessage>  bufferedMsgs=  collection.getBufferedMessages();
            for(BufferedMessage msg: bufferedMsgs){
                list.add(msg.message);
            }
        }
        return list;
    }
}
