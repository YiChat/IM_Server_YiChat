package tigase.apns;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;

public class APNSManager {
	
	private static final Logger     log = Logger.getLogger(APNSManager.class.getName());
	
	private static final String File_Name = "etc/init-apns.properties";
	
	public static File devCert; 
	
	private static String certPwd; 
	
	private static Properties propts;
	
	private static int executor_size;
	
	static ScheduledThreadPoolExecutor schedule = new ScheduledThreadPoolExecutor(executor_size);
	
	private static Map<String, String> tokens = new ConcurrentHashMap<String, String>();
	
	private static Map<String, Integer> msg_counts = new ConcurrentHashMap<String, Integer>();
	
	private static int maxConnections;
	
	static {
		try {
			propts = new Properties();
			propts.load(new FileInputStream(File_Name));
			
			devCert = new File("etc/" + propts.getProperty("cert_file"));
			
			certPwd = propts.getProperty("pwd");
			
			executor_size = Integer.valueOf(propts.getProperty("executor_size", "15")); 
			
			schedule = new ScheduledThreadPoolExecutor(executor_size);
			
			maxConnections = Integer.valueOf(propts.getProperty("maxConnections", "15"));
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	
	private static ApnsService apnsService = APNS.newService().asPool(schedule, maxConnections).withCert(devCert.getAbsolutePath(), certPwd).withSandboxDestination().build();

	public static void pushMsg(String uid, String message){
		
		final String token = tokens.get(uid);
		if(token == null)
			return;
		
		msgCountAdd(uid);
		
		int count = msg_counts.get(uid);
		
		String push = APNS.newPayload().alertBody(message).badge(count).sound("newMsg.mp3").build();
		apnsService.push(token, push);
		
		
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "#IOS push: uid:{0} token:{1} message:{2}", new String[]{ uid, token, message});
		}
	}

	public static void addToken(String uid, String token) {
		if(token != null)
			tokens.put(uid, token);
	}
	
	public static void delToken(String uid) {
		tokens.remove(uid);
	}
	
	public static String getToken(String uid) {
		return tokens.get(uid);
	}
	
	public static void initToken(Map<String, String> $tokens){
		tokens.putAll($tokens);
	}
	
	public static void msgCountAdd(String uid){
		if(msg_counts.get(uid) == null){
			msg_counts.put(uid, 1);
		}else{
			int temp = msg_counts.get(uid);
			msg_counts.put(uid, ++temp);
		}
	}
	
	public static void msgCountClear(String uid){
		msg_counts.remove(uid);
	}
}
