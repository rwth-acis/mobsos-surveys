# \<mobsos-questionnaire-editor\>

An editor for creating MobSOS questionnaires.

# Demo

See an [amazing demo here](https://rwth-acis.github.io/mobsos-questionnaire-elements/components/mobsos-questionnaire-elements/#mobsos-questionnaire-editor).

# How to Use

After importing the html element, use the following line of code to
show an editor for your MobSOS questionnaire.

```
<mobsos-questionnaire-editor></mobsos-questionnaire-editor>
```

# How to Develop

Please read here, how to modify and test this element.

## Install the Polymer-CLI

First, make sure you have the [Polymer CLI](https://www.npmjs.com/package/polymer-cli) installed. Then run `polymer serve` to serve your application locally.

## Viewing Your Application

```
$ polymer serve
```

## Building Your Application

```
$ polymer build
```

This will create a `build/` folder with `bundled/` and `unbundled/` sub-folders
containing a bundled (Vulcanized) and unbundled builds, both run through HTML,
CSS, and JS optimizers.

You can serve the built versions by giving `polymer serve` a folder to serve
from:

```
$ polymer serve build/bundled
```

## Running Tests

```
$ polymer test
```

Your application is already set up to be tested via [web-component-tester](https://github.com/Polymer/web-component-tester). Run `polymer test` to run your application's test suite locally.
