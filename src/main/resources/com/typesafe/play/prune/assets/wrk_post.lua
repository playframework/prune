-- example HTTP POST script which demonstrates setting the
-- HTTP method, body, and adding a header

wrk.method = "POST"
wrk.body   = "name=PlayFramework&age=10&email=play@playframework.com&twitter=playframework&github=playframework"
wrk.headers["Content-Type"] = "application/x-www-form-urlencoded"
