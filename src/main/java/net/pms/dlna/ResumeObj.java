package net.pms.dlna;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pms.PMS;

public class ResumeObj {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResumeObj.class);

	/**
	 * The length of time taken from the end of the video to assume the user
	 * is done viewing. In other words it is an estimation of the length of
	 * the credits.
	 * 30000 = 30 seconds
	 */
	private static final long BACK_FACTOR = 30000;

	/**
	 * The minimum length of time elapsed in the video before we allow resuming
	 * 1000 = 1 second
	 */
	private static final long MIN_RESUME_TIME = 1000;

	private File file;
	private long offsetTime;
	private long resDuration;

	private static File resumePath() {
		File path = new File(PMS.getConfiguration().getDataFile("resume"));
		path.mkdirs();
		return path;
	}

	private static File resumeFile(DLNAResource r) {
		String fName = r.getName() + "_hash_" + r.resumeHash() + ".resume";
		return new File(resumePath().getAbsolutePath() + File.separator + fName);
	}
	
	public static File[] resumeFiles() {
		File path = resumePath();
		return path.listFiles();
	}
	
	public static ResumeObj create(DLNAResource r) {
		if (!PMS.getConfiguration().getResume()) {
			// resume is off bail early
			return null;
		}
		File f = resumeFile(r);
		if (!f.exists()) {
			// no file no resume
			return null;
		}
		ResumeObj res = new ResumeObj(f);
		res.read();
		if (res.noResume()) {
			return null;
		}
		if (r.getMedia() != null && r.getMedia().getDurationInSeconds() != 0) {
			r.getMedia().setDuration((double)res.resDuration);
		}
		return res;
	}

	public static ResumeObj store(DLNAResource r, long startTime) {
		File f = resumeFile(r);
		ResumeObj obj = new ResumeObj(f);
		obj.stop(startTime, (long) r.getMedia().getDurationInSeconds() * 1000);
		if (obj.noResume()) {
			return null;
		}
		return obj;
	}

	public ResumeObj(File f) {
		offsetTime = 0;
		resDuration = 0;
		file = f;
	}

	public void read() {
		try {
			try (BufferedReader in = new BufferedReader(new FileReader(file))) {
				String str;
				while ((str = in.readLine()) != null) {
					String[] tmp = str.split(",");
					offsetTime = Long.parseLong(tmp[0]);
					if (tmp.length > 1) {
						resDuration = Long.parseLong(tmp[1]);
					}
					break;
				}
			}
		} catch (IOException e) {
		}
	}

	private static void write(long time, long duration, File f) {
		try {
			try (BufferedWriter out = new BufferedWriter(new FileWriter(f))) {
				out.write(time + "," + duration);
				out.flush();
			}
		} catch (IOException e) {
		}
	}

	public boolean noResume() {
		return (offsetTime == 0);
	}

	public long getTimeOffset() {
		return offsetTime;
	}

	public void stop(long startTime, long expDuration) {
		long now = System.currentTimeMillis();
		long thisPlay = now - startTime;
		long duration = thisPlay + getTimeOffset();

		if (expDuration > MIN_RESUME_TIME) {
			if (duration >= (expDuration - BACK_FACTOR)) {
				// We've seen the whole video (likely)
				file.delete();
				return;
			}
		}
		if (thisPlay < BACK_FACTOR) {
			// we assume that we're done here
			file.delete();
			return;
		}
		if (thisPlay < MIN_RESUME_TIME) {
			// to short to resume (at all)
			return;
		}
		offsetTime = duration;
		resDuration = expDuration;
		write(duration - BACK_FACTOR, expDuration, file);
	}
}