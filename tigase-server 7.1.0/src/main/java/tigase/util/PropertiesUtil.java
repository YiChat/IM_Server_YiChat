package tigase.util;

import java.io.FileInputStream;
import java.util.Properties;

public class PropertiesUtil {
	
	private static final String File_Name = "etc/init-redis.properties";

	private static Properties propts;

	static {

		try {
			propts = new Properties();
			propts.load(new FileInputStream(File_Name));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static String get(String name, String defaultValue) {
		
		String value = (String) propts.get(name);

		return value != null ? value : defaultValue;
	}
	
	public static String get(String name) {
		
		return (String) propts.get(name);
	}
//
//	@SuppressWarnings("unchecked")
//	public static Context getInitialContext() {
//		Context context = null;
//
//		String jndiFactory = Props.get("jndi.factory");
//		String providerUrl = Props.get("jndi.provider.url");
//
//		Hashtable env = new Hashtable();
//		env.put(Context.INITIAL_CONTEXT_FACTORY, jndiFactory);
//		env.put(Context.PROVIDER_URL, providerUrl);
//
//		try {
//			context = new InitialContext(env);
//		} catch (Exception ex) {
//			ex.printStackTrace();
//		}
//
//		return context;
//	}
}
