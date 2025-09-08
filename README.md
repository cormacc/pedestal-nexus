# cormacc/pedestal-nexus

A simple library to support data-driven handler implementation in [Pedestal](https://pedestal.io) using the [Nexus](https://github.com/cjohansen/nexus) action dispatch library.

This repo is very much WIP - untested and poorly documented.

## Usage

The todo example from the pedestal tutorials has been re-written using action-dispatch.

See [todo example](./examples/src/examples/todos.clj)

FIXME: write usage documentation!

Invoke a library API function from the command-line:

    $ clojure -X cormacc.pedestal-nexus/foo :a 1 :b '"two"'
    {:a 1, :b "two"} "Hello, World!"

Run the project's tests (they'll fail until you edit them):

    $ bb test     # run Clojure tests
    $ bb test:bb  # run Babashka tests
    $ bb test:all # run both Clojure and Babashka tests

Run the project's CI pipeline and build a JAR (this will fail until you edit the tests to pass):

    $ bb ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ bb install

Run the projects's CI pipeline and deploy it to Clojars --
needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables:

    $ bb ci:deploy

Your library will be deployed to net.clojars.cormacc/pedestal-nexus on clojars.org by default.

## Acknowledgments

This library was inspired by (and partly adapted from) a similar effort by Ovi Stoica ([@ovistoica](https://github.com/ovistoica)) for Ring, and the nexus library and learning materials provided by Christian Johansen ([@cjohansen](https://github.com/cjohansen)) and Magnar Sveen ([@magnars](https://github.com/magnars)) 

## License
Copyright © 2025 Cormac Cannon
Distributed under the [MIT License](https://opensource.org/license/mit).
