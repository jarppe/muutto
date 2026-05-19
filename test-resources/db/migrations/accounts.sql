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
