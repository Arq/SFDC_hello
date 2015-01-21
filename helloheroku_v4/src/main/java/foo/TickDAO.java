package foo;

import java.sql.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.pool.impl.GenericObjectPool.Config;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
public class TickDAO {
    static JedisPool jedisPool;
    private static final String TICKCOUNT_KEY = "tickcount";
    private static String dbUrl;

    static {
		Pattern REDIS_URL_PATTERN = Pattern.compile("^redis://([^:]*):([^@]*)@([^:]*):([^/]*)(/)?");
		Matcher matcher = REDIS_URL_PATTERN.matcher(System.getenv("REDISTOGO_URL"));
		matcher.matches();
		
		Config config = new Config();
		config.testOnBorrow = true;
		System.out.println("============== ["+matcher.group(0)+"] ==============");
		System.out.println("============== ["+matcher.group(1)+"] ==============");
		System.out.println("============== ["+matcher.group(2)+"] ==============");
		System.out.println("============== ["+matcher.group(3)+"] ==============");
		System.out.println("============== ["+matcher.group(4)+"] ==============");
		System.out.println("============== ["+matcher.group(5)+"] ==============");
		String group2 = null;
		group2 = matcher.group(2);  //redis.clients.jedis.exceptions.JedisDataException: ERR Client sent AUTH, but no password is set
		group2 = null;				//redis.clients.jedis.exceptions.JedisDataException: ERR Client sent AUTH, but no password is set
		jedisPool = new JedisPool(config, matcher.group(3), Integer.parseInt(matcher.group(4)), Protocol.DEFAULT_TIMEOUT, group2);
        dbUrl = System.getenv("DATABASE_URL");
        dbUrl = dbUrl.replaceAll("postgres://(.*):(.*)@(.*)", "jdbc:postgresql://$3?user=$1&password=$2");
    }
 	
	/* nowa wersja ponizej
	public int getTickCount() throws SQLException {
		return getTickcountFromDb();
	}
	*/
    public int getTickCount() throws SQLException {
        Jedis jedis = jedisPool.getResource();
        int tickcount = 0;
        String tickcountValue = jedis.get(TICKCOUNT_KEY);
        if (tickcountValue != null) {
            System.out.println("read from redis cache");
            tickcount = Integer.parseInt(tickcountValue);
        }
        else {
            tickcount = getTickcountFromDb();
            jedis.setex(TICKCOUNT_KEY, 30, String.valueOf(tickcount));
        }
        jedisPool.returnResource(jedis);
    
        return tickcount;
    }	
	public static int getScalarValue(String sql) throws SQLException {
		Connection dbConn = null;
		try {
			dbConn = DriverManager.getConnection(dbUrl);
			Statement stmt = dbConn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			rs.next();
			System.out.println("read from database");
			return rs.getInt(1);
		} finally {
			if (dbConn != null) dbConn.close();
		}
	}
	
	private static void dbUpdate(String sql) throws SQLException {
		Connection dbConn = null;
		try {
			dbConn = DriverManager.getConnection(dbUrl);
			Statement stmt = dbConn.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			if (dbConn != null) dbConn.close();
		}
	}
	
	private int getTickcountFromDb() throws SQLException {
		return getScalarValue("SELECT count(*) FROM ticks");
	}
	
	public static void createTable() throws SQLException {
		System.out.println("Creating ticks table.");
		dbUpdate("DROP TABLE IF EXISTS ticks");
		dbUpdate("CREATE TABLE ticks (tick timestamp)");
	}
	
	public void insertTick() throws SQLException {
		dbUpdate("INSERT INTO ticks VALUES (now())");
	}
 	
}