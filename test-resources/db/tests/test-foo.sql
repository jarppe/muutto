--
-- Example test case:
--

create or replace function test.test_case_test_something_working()
  returns void
  language plpgsql
as $$
  begin
    perform test_assertTrue('something should be true', true);
  end;
$$;


create or replace function test.test_case_test_something_failing()
  returns void
  language plpgsql
as $$
  begin
    perform test_assertTrue('something else', false);
  end;
$$;


create or replace function test.test_case_test_something_throwing()
  returns void
  language plpgsql
as $$
  begin
    raise EXCEPTION 'oh no';
  end;
$$;
