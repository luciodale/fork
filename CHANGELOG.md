# Changelog
All notable changes to this project will be documented in this file.

## [0.1.1]
### Added
- Dynamic array input and more built-in inputs

## [1.0.0]
### Added
- Support for any side effect free validation
- `:external-errors` key to differenciate from input `:errors`
- `:form-id` to let fork know about the inputs

### Changed
- `:initial-values` not required
- Safe calls to `(values "input")` or `(touched "input")` when no value is set

### Removed
- Built-in validation
- Array input support

## [1.0.2]
### Added
- `set-touched` handler

### Changed
- Bulma dropdowns take a list of maps as options rather than a map

## [1.1.0]
### Added
- `send-server-request` handler

## [1.2.0]
### Added
- `component-did-mount` handler
- `initial-touched` handler

### Changed
- `set-touched` args should not be wrapped in a sequence.

### Removed
- enable disable re-frame handlers

## [1.2.2]
### Added
- `on-submit-response` handler
- `set-status-code` re-frame handler

### Removed
- set-external-errors

## [1.2.4]
### Added
- `:throttle` option to `on-submit-response`

## [1.2.5]
### Added
- `reset` handler to props

## [1.2.6]
### Changed
- `dirty?` submit handler into `dirty`. Now, the variable returns either `nil` or a map of changed values.

## [2.0.0]
### Changed
- to use *Fork* now you need to require either `fork.re-frame` or `fork.reagent`. For the bulma UI kit require `fork.bulma`
- `:on-submit-response` map has been removed. It's better to delegate the server for the error messages
- `:on-submit-response-message` has changed into `:on-submit-server-message`
- `:set-status-code` has changed into `:set-server-message`
- `:set-waiting`, `:set-submitting`, and `:set-server-message` are now globally accessible from your `fork.re-frame` or `fork.reagent` namespaces.
- the re-frame interceptors `:on-submit` and `:clean` have been removed. Therefore, now you need to set submitting? to true yourself (through the global helper).

## [2.0.4]
### Added
- `errors`, `dirty`, and `touched` arguments to the `send-server-request` user provided function.

## [2.1.0]
### Changed
- `send-server-request` only takes two params. The first one is a map that must contain a `:name` key along with optional `:value`, `:evt`, `:debounce`, `:throttle` keys. The the second one is the user provided fn for the server http request.

## [2.1.1]
### Added
- `keywordize-keys` config param. It casts all strings to keywords, allowing the developer to only deal with keywords across the form components and handlers.
