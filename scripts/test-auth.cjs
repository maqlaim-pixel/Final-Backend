// Runs against a unique disposable PostgreSQL schema. Never alters public data.
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')
const crypto = require('crypto')
const root = path.resolve(__dirname, '..')
const localFile = path.join(root, '.env.docker')
const local = fs.existsSync(localFile) ? Object.fromEntries(fs.readFileSync(localFile, 'utf8').split(/\r?\n/)
  .filter(line => /^[A-Z_]+=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)])) : {}
const schema = 'tv_auth_test_' + crypto.randomBytes(8).toString('hex')
const user = process.env.AUTH_TEST_DB_USER || local.POSTGRES_USER || 'postgres'
const password = process.env.AUTH_TEST_DB_PASSWORD || local.POSTGRES_PASSWORD
if (!password) throw new Error('Set AUTH_TEST_DB_PASSWORD for the local test database')
const env = { ...process.env, PGPASSWORD: password, AUTH_TEST_DB_USER: user, AUTH_TEST_DB_PASSWORD: password,
  AUTH_TEST_JDBC_URL: `jdbc:postgresql://localhost:5432/postgres?currentSchema=${schema}`,
  AUTH_TEST_JWT_SECRET: crypto.randomBytes(48).toString('hex'),
  JAVA_HOME: process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-17' }
const psql = process.env.PSQL_PATH || 'C:/Program Files/PostgreSQL/17/bin/psql.exe'
const maven = process.env.MAVEN_PATH || 'C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.1/plugins/maven-plugin/lib/maven3/bin/mvn.cmd'
function sql(statement) {
  const result = spawnSync(psql, ['-w', '-h', 'localhost', '-U', user, '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-c', statement], { env, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(result.stderr || 'PostgreSQL command failed')
}
sql(`CREATE SCHEMA ${schema}`)
{
  const log = fs.openSync(path.join(root, 'auth-tests.log'), 'w')
  const result = spawnSync('powershell.exe', ['-NoProfile', '-Command', `& '${maven.replaceAll("'", "''")}' '-Dmaven.repo.local=C:\\Users\\user\\.m2\\repository' -o verify; exit $LASTEXITCODE`], {
    cwd: root, env, stdio: ['ignore', log, log] })
  fs.closeSync(log)
  process.exitCode = result.status ?? 1
  console.log(`Authentication test exit code: ${process.exitCode}; details: auth-tests.log`)
}
console.log(`Test data retained in schema ${schema}; no existing schema was changed or deleted.`)
