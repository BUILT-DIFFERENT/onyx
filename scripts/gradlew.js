const { spawn } = require("node:child_process");
const path = require("node:path");

const args = process.argv.slice(2);
const isWindows = process.platform === "win32";

const cwd = process.cwd();
if (isWindows) {
  const gradlewBat = path.join(cwd, "gradlew.bat");
  const child = spawn("cmd.exe", ["/c", gradlewBat, ...args], {
    stdio: "inherit",
    cwd,
  });
  child.on("exit", (code) => process.exit(code ?? 1));
} else {
  const gradlew = path.join(cwd, "gradlew");
  const child = spawn(gradlew, args, { stdio: "inherit", cwd });
  child.on("exit", (code) => process.exit(code ?? 1));
}
