package com.itxiaox.android.xutils.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;

import com.itxiaox.android.xutils.app.SystemUtils;
import com.itxiaox.android.xutils.fun.OXConsumer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * UncaughtException处理类,当程序发生Uncaught异常的时候,由该类来接管程序,并记录发送错误报告.
 * 
 * @author way
 * 
 */
public class SysCrashHandler implements UncaughtExceptionHandler {
	// private static final String TAG = "RWCrashHandler";

	private UncaughtExceptionHandler mDefaultHandler;// 系统默认的UncaughtException处理类
	@SuppressLint("StaticFieldLeak")
	private static SysCrashHandler INSTANCE = new SysCrashHandler();// CrashHandler实例
	private Context mContext;// 程序的Context对象
	private Map<String, String> info = new HashMap<String, String>();// 用来存储设备信息和异常信息
	private SimpleDateFormat format = new SimpleDateFormat(
			"yyyy-MM-dd HH-mm-ss", Locale.getDefault());// 用于格式化日期,作为日志文件名的一部分
	public static String RootLog = "xczt";
	public static String Log ;
	/** 保证只有一个CrashHandler实例 */
	private SysCrashHandler() {

	}

	/** 获取CrashHandler实例 ,单例模式 */
	public static SysCrashHandler getInstance() {
		return INSTANCE;
	}

	public interface CrashListener{
		void onCrash(Throwable e);
	}
	private CrashListener listener;
	/**
	 * 初始化
	 * 
	 * @param context
	 */
	public void init(Context context, String pathLog, CrashListener crashListener) {
		mContext = context;
		this.listener  = crashListener;
		Log = pathLog;
		LogUtils.d("crash log保存路径："+Log);
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();// 获取系统默认的UncaughtException处理器
		Thread.setDefaultUncaughtExceptionHandler(this);// 设置该CrashHandler为程序的默认处理器
	}

	/**
	 * 当UncaughtException发生时会转入该重写的方法来处理
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		String sqlException = "android.database.sqlite.SQLiteDatabaseCorruptException";
		if (!ex.toString().contains(sqlException)) {// 数据库损坏
			if (!handleException(ex) && mDefaultHandler != null) {
				// 如果自定义的没有处理则让系统默认的异常处理器来处理
				mDefaultHandler.uncaughtException(thread, ex);
			} else {
				if (listener!=null)listener.onCrash(ex);
			}
		}
	}

	/**
	 * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
	 * 
	 * @param ex
	 *            异常信息
	 * @return true 如果处理了该异常信息;否则返回false.
	 */
	private boolean handleException(Throwable ex) {
		if (ex == null)
			return false;
		new Thread() {
			@Override
			public void run() {
				Looper.prepare();
//				SystemUtils.reStartActivity(mContext);
				Looper.loop();
			}
		}.start();
		// 收集设备参数信息
		collectDeviceInfo(mContext);
		// 保存日志文件
		saveCrashInfo2File(ex);
		return true;
	}

	/**
	 * 收集设备参数信息
	 * 
	 * @param context
	 */
	private void collectDeviceInfo(Context context) {
		try {
			PackageManager pm = context.getPackageManager();// 获得包管理器
			PackageInfo pi = pm.getPackageInfo(context.getPackageName(),
					PackageManager.GET_ACTIVITIES);// 得到该应用的信息，即主Activity
			if (pi != null) {
				String versionName = pi.versionName == null ? "null"
						: pi.versionName;
				String versionCode = pi.versionCode + "";
				info.put("versionName", versionName);
				info.put("versionCode", versionCode);
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		Field[] fields = Build.class.getDeclaredFields();// 反射机制
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				info.put(field.getName(), field.get("").toString());
//				Log.d("180", field.getName() + ":" + field.get(""));
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	private String saveCrashInfo2File(Throwable ex) {
		StringBuffer sb = new StringBuffer();
		for (Map.Entry<String, String> entry : info.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			sb.append(key).append("=").append(value).append("\r\n");
		}
		Writer writer = new StringWriter();
		PrintWriter pw = new PrintWriter(writer);
		ex.printStackTrace(pw);
		Throwable cause = ex.getCause();
		// 循环着把所有的异常信息写入writer中
		while (cause != null) {
			cause.printStackTrace(pw);
			cause = cause.getCause();
		}
		pw.close();// 记得关闭
		String result = writer.toString();
		sb.append(result);
		// 保存文件
		String time = format.format(new Date());
		String fileName = time + "_Log.txt";
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -30);
		String day = format.format(calendar.getTime());
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			try {
				File dir = new File(Log);
				if (!dir.exists())
					dir.mkdir();

				FileOutputStream fos = new FileOutputStream(dir + "/"
						+ fileName);
				fos.write(sb.toString().getBytes());
				fos.close();
				return fileName;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

}