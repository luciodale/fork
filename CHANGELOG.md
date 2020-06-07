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
