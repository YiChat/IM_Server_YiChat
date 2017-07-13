package tigase.receipt;

import java.util.logging.Logger;

import tigase.server.Packet;

public class MessageReceiptsStoreFactory {
	private static final Logger log    = Logger.getLogger(MessageReceiptsStoreFactory.class.getName());
    private final static MessageReceiptsStoreFactory instance = new MessageReceiptsStoreFactory();

    private FactoryStrategy proxy = null;

    private MessageReceiptsStoreFactory() {

    }

    public MessageReceiptsStore create(Packet packet) {
        if (null != this.proxy) {
            return this.proxy.match(packet);
        } else {
        	log.warning("No set MessageReceiptsStoreFactory strategy");
        }
        return null;
    }

    public static MessageReceiptsStoreFactory getInstance() {
        return instance;
    }

    public void setFactoryStrategy(FactoryStrategy strategy) {
        this.proxy = strategy;
    }

    public interface FactoryStrategy {
        public MessageReceiptsStore match(Packet packet);
    }
}
