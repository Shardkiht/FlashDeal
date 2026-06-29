wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["authentication"] = "填写token"
wrk.body = '{"id":数据库中的用户id值}'