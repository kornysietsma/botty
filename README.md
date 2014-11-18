# botty

A simple library for writing irc bots, based on ircjl and some core.async glue

If you want something more powerful consider lazybot - I wrote this as I couldn't use lazybot easily (no mongodb on servers, wanted to run via uberjar) and I just wanted some simple bots.

## Usage

Bots need to implement two kinds of handlers:

- tick handlers, these get called regularly (you control the interval) and can do anything background-y.
- message handlers, these get called in response to irc activity in a channel the bot is watching

The handlers get a copy of a "world" structure which is the current bot state.  The handlers return
a mutated version of the world, which means they can change the bot behaviour dynamically.

... to be continued

See also [http://github.com/kornysietsma/hodor] for a simple bot using this

You can run this bot with the right defaults - it will just print "tick" a lot, and let you quit.

## License

Licensed under the EPL, same as Clojure itself.