package playground.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.Response

@Api
fun hello(ctx: ApiContext) {
    ctx.res.body = Response.Body.text("hello world")
}