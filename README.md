# muutto - Database migrations the easy way

## Use

It's probably easiest to explain this by examples.

Let's assume that you have PostgreSQL running in docker container, like this:

`docker-compose.yaml`:

```yaml
---
services:
  db:
    image: postgres:18-alpine
    init: true
    restart: on-failure
    environment:
      - POSTGRES_DATABASE=postgres
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
```

Start the server:

```bash
$ docker compose up -d
```

Create `muutto.edn` configuration file:

`muutto.edn`:

```clojure
{:migrations   ["resources/db/migrations"]
 :tests        ["resources/db/test/tests"]
 :username     "postgres"
 :env          {:postgres {:dbname "postgres"
                           :locked true}
                :dev      {:dbname "hello_dev"}
                :test     {:dbname     "hello_test"
                           :migrations ["resources/db/test/migrations"]}
                :prod     {:dbname    "hello_prod"
                           :protected true}}
 :default-env  {:migrate :dev
                :test    :test}
 :psql-wrapper "docker compose exec db"
 :opts         {:verbose true}}
```

The above configuration explained:

- The migration files are in `resources/db/migrations`
- The test files are in `resources/db/test/tests`
- The default database username to use is `postgres`
- We have four database environments: `postgres`, `dev`, `test`, and `prod`
- The `postgres` is locked, so you can't migrate it or drop it
- The `prod` is protected, so you can migrarte to it, but you can not drop it
- When using environment `test` include migrations from directory `resources/db/test/migrations`
- The default environment for `migrate` command is `dev` and for `test` command `test`
- Instead of running `psql` directly on host computer, run it in the docker container `db`
- Set the `verbose` option on (same could be done from command-line using option `-v`)

Next create few migrations to `resources/db/migrations` directory:

`resources/db/migrations/init.sql`:

```sql
-- 
-- Initialize database:
--

alter default privileges revoke execute on functions from public; 

do $$
begin
  create role example_app
    with
      login password 'example_auth';
  exception when duplicate_object
    then null;
end
$$;

create schema example;

do $$
begin
  execute format('alter database %I set search_path to example, pgunit, test, public', current_database());
end
$$;

grant usage on schema example to example_app;
```

_Note:_ The migration script has multiple statements. There is no special syntax required for including multiple statements to one migration file. In fact, you can put anything to migration file as long as the `psql` command accepts it.

_Note:_ The reason for wrapping the role creation above is that the roles are cluster wide resources in postgres. Wrapping the role creation into try/catch allows this script to be run multiple times in different databases. Using onbe database for development and another for local testing is common. Without the try/catch the script would fail when applied to different database in same database *server*.

`resources/db/migrations/accounts.sql`:

```sql
-- 
-- Example app accounts:
--

-- muutto:requires: init.sql

create table accounts (
  account_id  uuid not null primary key default uuidv7(),
  email       text not null,
  username    text not null
);

-- Make sure emails are unique:

alter table accounts add constraint account_emails_unique unique (email);

-- Give example_app wide access to accounts table:

grant
  select,
  insert, 
  update (email, username), 
  delete 
on accounts 
  to example_app;
```

_Note:_ The comment `-- muutto:requires: init.sql` is a regular comment for postgres, but it has a special meaning for `muutto`. It tells that this migration depends on the `init.sql` migration, so it this must be applied after the `init.sql`.

### Migration

The moment of truth, it's time to migrate the `dev` database:

```bash
$ muutto migrate dev
muutto: migrating database hello_dev
creating database        ok
initializing database    ok
   resources/db/migrations/init.sql      migrated (0.099 sec)
   resources/db/migrations/accounts.sql  migrated (0.088 sec)
migrated 2 files in 0.187 sec
```

The database was create, initilized for migrations, and two migrations were applied. Let's try the same again:

```bash
$ muutto migrate dev
muutto: migrating database hello_dev
   resources/db/migrations/init.sql      skipped  (0.000 sec)
   resources/db/migrations/accounts.sql  skipped  (0.000 sec)
migrated 2 files in 0.000 sec
```

This time database was not created or initialized, and both migrations were skipped as they already existed in database.

### List migrations

Let's see the migrations we have done:

```bash
$ muutto list
muutto: Error: environment is required
muutto: Try: muutto --help
```

Ups. Looks like we need to specify the environment. Lets try again:

```bash
$ muutto list dev
muutto: listing database hello_dev migrations
File:                                   | Applied:
----------------------------------------|--------------------
resources/db/migrations/init.sql        | 2026/05/19 19:37:00
resources/db/migrations/accounts.sql    | 2026/05/19 19:37:00
```

Could we make the `list` command to use database `dev` by default. Let's edit `muutto.edn` from this:

```clojure
 :default-env  {:migrate :dev
                :test    :test}
```

to this:

```clojure
 :default-env  {:migrate :dev
                :test    :test
                :list    :dev}
```

Now the default environment for `list` command is `dev`:

```bash
$ muutto list
muutto: listing database hello_dev migrations
File:                                   | Applied:
----------------------------------------|--------------------
resources/db/migrations/init.sql        | 2026/05/19 19:37:00
resources/db/migrations/accounts.sql    | 2026/05/19 19:37:00
```

Excellent, this saves us 4 keystrokes.

Let's add a new migration. This one adds a new role and a super-duper secret table for passwords:

`resources/db/migrations/passwords.sql`:

```sql
-- muutto:requires: accounts.sql

-- 
-- Add role that can view accounts:
--


do $$
begin
  create role example_user
    with
      login password 'example_user';
  exception when duplicate_object
    then null;
end
$$;

grant usage on schema example to example_user;

grant select on accounts to example_user;

--
-- Add the passwords table for secrets:
--

create table passwords (
  account_id  uuid  not null references accounts (account_id) on delete cascade,
  password    text  not null
);

-- Give access to only example_app, not example_user:

grant
  select,
  insert, 
  update, 
  delete 
on passwords 
  to example_app;
```

Migrate the change. In `muutto.env` we have configured the default database environment for `migrate` command to be `dev`, so we don't need to specify that.

```bash
$ muutto migrate
muutto: migrating database hello_dev
   resources/db/migrations/init.sql       skipped  (0.000 sec)
   resources/db/migrations/accounts.sql   skipped  (0.000 sec)
   resources/db/migrations/passwords.sql  migrated (0.092 sec)
migrated 3 files in 0.092 sec
```

Success, our new migration was applied.

### psql shell

Let's run a quick ad-hoc test to see if the role permissions we specified work as expected. Let's open an interactive `psql` shell to `dev` and do some quick testing:

```bash
$ muutto psql dev
hello_dev=# set role example_app;
hello_dev=> select * from passwords;
 account_id | password_hash
------------+---------------
(0 rows)

hello_dev=> reset role;
hello_dev=# set role example_user;
hello_dev=> select * from passwords;
ERROR:  permission denied for table passwords
STATEMENT:  select * from passwords;
hello_dev=> ^D
$ 
```

Looks good. The `psql` command can do more, see the documentation for it later in this doc.

Before we go further ther's one more thing I think you should know. Open the `passwords.sql` file on your editor and make a change. Any change. Or just run: `$ echo '-- This is the end' >> resources/db/migrations/passwords.sql` to make a change to the migration file.

Then try to migrate:

```bash
$ muutto migrate
muutto: migrating database hello_dev
   resources/db/migrations/init.sql       skipped  (0.000 sec)
   resources/db/migrations/accounts.sql   skipped  (0.000 sec)
   resources/db/migrations/passwords.sql  error: SQL migration file resources/db/migrations/passwords.sql file has been changed
  migrated file hash: a4aefc4596fa1155392b3ca12d9833d0d59da261208e3277d70273f78a70b4a7
   current file hash: 24c514a7a6ece58d3fbe75a2597728d11f0fb9e38d92397ffe855f62ed371159
muutto: migration halted
```

So `muutto` detected that the migration file is changed it and exited with error message. This for your own good. Don't edit migrations that are already applied to databases.

### Testing

Ad-hoc testing with `muutto psql` are find for simple tests, but for proper testing, you need a test library. The good news is that there is a good library available, and `muutto` comes with it bundled in.

The excellent database unit testing library is [PGunit](https://github.com/adrianandrei-ca/pgunit). You should definitely give a quick read on PGunit documentation.

Let's add a test migration to set up the test database:

`resources/db/test/migrations/init-tests.sql`:

```sql
--
-- To make testting easier, allow example app and user to use pgunit:
--

grant usage on schema pgunit to example_app, example_user;
```

This migration is in `resources/db/test/migrations` directory that is configured in `test` database environment. It will not be migrated to other databases.

Next add some simple tests for `accounts` table:

`resources/db/test/tests/test-accounts.sql`:

```sql
--
-- Test suite setup and teardown:
--


create or replace function test.test_setup_accounts()
  returns void
  language plpgsql
as $$
begin
  insert into accounts (username, email) values ('tiger', 'tiger@example.com');
end;
$$;

create or replace function test.test_teardown_accounts()
  returns void
  language plpgsql
as $$
begin
  delete from accounts where email = 'tiger@example.com';
end;
$$;

--
-- Tests:
--

create or replace function test.test_case_accounts_app_role()
  returns void
  language plpgsql
as $$
declare
  found_username text;
begin
  set role example_app;
  select username into found_username from accounts where email = 'tiger@example.com';
  perform pgunit.test_assertTrue('account found', found_username = 'tiger');
end;
$$;


create or replace function test.test_case_accounts_user_role()
  returns void
  language plpgsql
as $$
declare
  found_username text;
begin
  set role example_user;
  select username into found_username from accounts where email = 'tiger@example.com';
  perform pgunit.test_assertTrue('account found', found_username = 'tiger');
end;
$$;
```

Lets try the `test` command:

```bash
$ muutto test
uutto: run db tests on database hello_test
creating database                                         ok (0.297 sec)
installing PGUnit                                         ok (0.176 sec)
migrating database
muutto: migrating database hello_test
   test-resources/db/migrations/init.sql       migrated (0.092 sec)
   test-resources/db/migrations/accounts.sql   migrated (0.082 sec)
   test-resources/db/migrations/passwords.sql  migrated (0.092 sec)
migrated 3 files in 0.267 sec
   test-resources/db/test/migrations/init-tests.sql  migrated (0.081 sec)
migrated 1 files in 0.081 sec
installing tests
  test-resources/db/test/tests/test-accounts.sql          ok (0.102 sec)
installed 1 tests in 0.190 sec
running tests
test_case_accounts_app_role                               ok (00.002 sec)
test_case_accounts_user_role                              ok (00.002 sec)
tests run in 0.108 sec
total time: 1.390 sec
```

Notice the important things:

- The `test` command used database `hello_test`
- Note that using different databases for development and testing is not required, but it is very handy to do so
- The `hello_test` database did not exists, so it was created automatically and the migrations were applied
- Finally the tests we're added to the database and ran

Let's add another test for passwords table to make sure that the `example_user` role can not read passwords:

`resources/db/test/tests/test-password.sql`:

```sql
--
-- Test suite setup and teardown:
--


create or replace function test.test_setup_password()
  returns void
  language plpgsql
as $$
begin
  insert into accounts (username, email) values ('tiger', 'tiger@example.com');
  insert into passwords (account_id, password) values 
    (
      (select id from accounts where email = 'tiger@example.com'),
      'hunter2'
    );
end;
$$;

create or replace function test.test_teardown_password()
  returns void
  language plpgsql
as $$
begin
  delete from accounts where email = 'tiger@example.com';
end;
$$;

--
-- Tests:
--

create or replace function test.test_case_password_app_role()
  returns void
  language plpgsql
as $$
declare
  found_password text;
begin
  set role example_app;
  select password into found_password 
  from
    passwords
  join 
    accounts on accounts.id = passwords.account_id
  where
    email = 'tiger@example.com';
  perform pgunit.test_assertTrue('got password', found_password = 'hunter2');
end;
$$;


create or replace function test.test_case_password_user_role()
  returns void
  language plpgsql
as $$
declare
  found_password text;
begin
  set role example_user;
  
  select password into found_password 
  from
    passwords
  join 
    accounts on accounts.id = passwords.account_id
  where
    email = 'tiger@example.com';
  
  perform pgunit.test_fail('should throw exception');

  exception 
    when others then
      perform pgunit.test_assertTrue(sqlstate = '42501');
      perform pgunit.test_assertTrue(sqlerrm = 'permission denied for table passwords');
end;
$$;
```

Run the tests:

```bash
$ muutto test
muutto: run db tests on database hello_test
creating database                                         skip (0.099 sec)
installing PGUnit                                         skip (0.085 sec)
migrating database
muutto: migrating database hello_test
   test-resources/db/migrations/init.sql       skipped  (0.000 sec)
   test-resources/db/migrations/accounts.sql   skipped  (0.000 sec)
   test-resources/db/migrations/passwords.sql  skipped  (0.000 sec)
migrated 3 files in 0.000 sec
   test-resources/db/test/migrations/init-tests.sql  skipped  (0.000 sec)
migrated 1 files in 0.000 sec
installing tests
  test-resources/db/test/tests/test-password.sql          ok (0.087 sec)
  test-resources/db/test/tests/test-accounts.sql          ok (0.095 sec)
installed 2 tests in 0.280 sec
running tests
test_case_accounts_app_role                               ok (00.002 sec)
test_case_accounts_user_role                              ok (00.001 sec)
test_case_password_app_role                               ok (00.001 sec)
test_case_password_user_role                              ok (00.001 sec)
tests run in 0.120 sec
total time: 0.857 sec
```

Everything works as expected.

### Manually creating databases

```bash
$ muutto create dev
muutto: creating database hello_dev
$ muutto init dev
muutto: initializing database hello_dev
```

### Droppimng databases

```bash
$ muutto drop dev
muutto: dropping database hello_dev
$ muutto drop prod
muutto: Error: database is protected
muutto: Try: muutto --help
$
```

### psql

You can run ad-hoc queries with the `psql` command:

```bash
$ muutto psql dev 'select now()'
              now
-------------------------------
 2026-05-19 07:02:58.116711+00
(1 row)
```

You can execute multiple statements on one go:

```bash
$ muutto psql dev "select current_database()" "select * from accounts" "select now()"
  current_database
------------------
 hello_dev
(1 row)

 account_id | email | username
------------+-------+----------
(0 rows)

             now
------------------------------
 2026-05-19 17:14:51.97739+00
(1 row)
```

You can run SQL statements from file by prefixing the file name with `@`:

```bash
$ echo "insert into accounts (username, email) values ('foo', 'foo@example.com')" > insert-foo.sql
$ muutto psql dev "select * from accounts" @insert-foo.sql "select * from accounts"
 account_id | email | username
------------+-------+----------
(0 rows)

              account_id              |      email      | username
--------------------------------------+-----------------+----------
 019e413d-be0c-7e74-a6fc-2812520daa57 | foo@example.com | foo
(1 row)
```

# Tips

If you want to be a bit more fancy, you can instruct `muutto` to use `rlwrap` wrapper. You could user `rlwrap` with every command, but it really makes sense only with interactive commands so let's see how we can add `rlwrap` to `psql` command.

Change the `:psql-wrapper` in `muutto.env` to this:

```clojure
 :psql-wrapper "rlwrap --always-readline 
                       --no-children
                       --prompt-colour=Blue
                       --multi-line
                       --multi-line-ext=.sql 
                  docker compose exec db"
```

Now try `muutto psql`, see how the prompt color is changed and how you can use command history and edit multi-line commands etc.

If your database is in Kubernetes, you could use something like this:

```clojure
 :context      "hello-context"
 :namespace    "dev"
 :psql-wrapper "kubectl
                   --context=${config:context}
                   --namespace=${config:namespace}
                   exec
                     --stdin ${config:tty:}
                     svc/db
                     --"

 ;; Change the namespace by setting different namespace values for each enviromnent:

                :dev      {:dbname    "hello_dev"
                           :namespace "dev}
                :prod     {:dbname    "hello_prod"
                           :protected true
                           :content   "hello-prod-content"
                           :namespace "hello_prod"}
```
