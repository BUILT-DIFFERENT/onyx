const { spawn, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const args = process.argv.slice(2);
const isWindows = process.platform === "win32";

const cwd = process.cwd();

function sanitizeJavaHome(rawValue) {
  if (!rawValue) return null;
  const trimmed = rawValue.trim().replace(/^"(.*)"$/, "$1");
  return trimmed.replace(/[\\/]+$/, "");
}

function pathExists(filePath) {
  try {
    return fs.existsSync(filePath);
  } catch {
    return false;
  }
}

function collectJavaCandidatesFrom(directory, matcher) {
  if (!pathExists(directory)) return [];
  try {
    const entries = fs.readdirSync(directory, { withFileTypes: true });
    return entries
      .filter((entry) => entry.isDirectory() && matcher(entry.name))
      .map((entry) => path.join(directory, entry.name, "bin", "java.exe"));
  } catch {
    return [];
  }
}

function resolveWindowsJava() {
  const candidates = [];
  const sanitizedJavaHome = sanitizeJavaHome(process.env.JAVA_HOME);
  if (sanitizedJavaHome) {
    candidates.push(path.join(sanitizedJavaHome, "bin", "java.exe"));
  }

  const programFiles = process.env.ProgramFiles || "C:\\Program Files";
  candidates.push(
    path.join(programFiles, "Android", "Android Studio", "jbr", "bin", "java.exe"),
  );
  candidates.push(
    ...collectJavaCandidatesFrom(
      path.join(programFiles, "Microsoft"),
      (name) => name.toLowerCase().startsWith("jdk"),
    ),
  );
  candidates.push(
    ...collectJavaCandidatesFrom(
      path.join(programFiles, "Eclipse Adoptium"),
      (name) => name.toLowerCase().startsWith("jdk"),
    ),
  );
  candidates.push(
    ...collectJavaCandidatesFrom(
      path.join(programFiles, "Java"),
      (name) => name.toLowerCase().startsWith("jdk"),
    ),
  );

  const javaExe = candidates.find(pathExists);
  if (!javaExe) return null;
  return {
    javaExe,
    javaHome: path.dirname(path.dirname(javaExe)),
  };
}

function detectJavaMajorVersion(javaExe) {
  try {
    const result = spawnSync(javaExe, ["-version"], {
      encoding: "utf8",
    });
    const versionOutput = `${result.stderr || ""}\n${result.stdout || ""}`;
    const match = versionOutput.match(/version "(\d+)(?:\.[^"]*)?"/);
    if (!match) return null;
    const major = Number.parseInt(match[1], 10);
    return Number.isNaN(major) ? null : major;
  } catch {
    return null;
  }
}

function resolvePosixJava() {
  const candidates = [];
  const sanitizedJavaHome = sanitizeJavaHome(process.env.JAVA_HOME);
  if (sanitizedJavaHome) {
    candidates.push(path.join(sanitizedJavaHome, "bin", "java"));
  }

  // Prefer stable JDK 17 installations on Linux environments.
  candidates.push(
    "/usr/lib/jvm/java-17-openjdk-amd64/bin/java",
    "/usr/lib/jvm/java-17-openjdk/bin/java",
    "/usr/lib/jvm/java-1.17.0-openjdk-amd64/bin/java",
  );

  const uniqueCandidates = [...new Set(candidates)].filter(pathExists);
  if (uniqueCandidates.length === 0) return null;

  const java17 = uniqueCandidates.find(
    (javaExe) => detectJavaMajorVersion(javaExe) === 17,
  );
  if (java17) {
    return {
      javaExe: java17,
      javaHome: path.dirname(path.dirname(java17)),
    };
  }

  const supported = uniqueCandidates.find((javaExe) => {
    const major = detectJavaMajorVersion(javaExe);
    return major !== null && major >= 17 && major < 25;
  });
  if (!supported) return null;

  return {
    javaExe: supported,
    javaHome: path.dirname(path.dirname(supported)),
  };
}

function resolveWindowsAndroidSdk() {
  const rawAndroidHome = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  const sanitizedAndroidHome = sanitizeJavaHome(rawAndroidHome);
  const candidates = [];
  if (sanitizedAndroidHome) {
    candidates.push(sanitizedAndroidHome);
  }

  const localAppData =
    process.env.LOCALAPPDATA ||
    (process.env.USERPROFILE
      ? path.join(process.env.USERPROFILE, "AppData", "Local")
      : null);
  if (localAppData) {
    candidates.push(path.join(localAppData, "Android", "Sdk"));
  }

  const sdkHome = candidates.find(pathExists);
  return sdkHome || null;
}

const env = { ...process.env };
if (isWindows) {
  const resolved = resolveWindowsJava();
  if (!resolved) {
    console.error(
      "Unable to find Java. Set JAVA_HOME to a JDK directory (for example C:\\Program Files\\Microsoft\\jdk-17.0.16.8-hotspot).",
    );
    process.exit(1);
  }
  env.JAVA_HOME = resolved.javaHome;
  const basePath = env.PATH || "C:\\Windows\\System32";
  env.PATH = `${path.dirname(resolved.javaExe)};${basePath}`;
  const sdkHome = resolveWindowsAndroidSdk();
  if (sdkHome) {
    env.ANDROID_HOME = sdkHome;
    env.ANDROID_SDK_ROOT = sdkHome;
  }
} else {
  const resolved = resolvePosixJava();
  if (resolved) {
    env.JAVA_HOME = resolved.javaHome;
    const basePath = env.PATH || "/usr/bin:/bin";
    env.PATH = `${path.dirname(resolved.javaExe)}:${basePath}`;
  }
}

if (isWindows) {
  const cmdExe = process.env.ComSpec || "C:\\Windows\\System32\\cmd.exe";
  const gradlewBat = path.join(cwd, "gradlew.bat");
  const child = spawn(cmdExe, ["/c", gradlewBat, ...args], {
    stdio: "inherit",
    cwd,
    env,
  });
  child.on("exit", (code) => process.exit(code ?? 1));
} else {
  const gradlew = path.join(cwd, "gradlew");
  const child = spawn(gradlew, args, { stdio: "inherit", cwd, env });
  child.on("exit", (code) => process.exit(code ?? 1));
}
