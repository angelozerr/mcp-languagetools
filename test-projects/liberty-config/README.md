# Liberty Config Test Project

This project contains intentionally invalid Liberty configuration files
for testing the Liberty Config Language Server integration in MCP Language Tools.

## Expected Diagnostics

### `bootstrap.properties`
| Line | Type | Message |
|------|------|---------|
| 5 | Error | The value `DEVx` is not valid for the property `com.ibm.ws.logging.console.format` |
| 8 | Warning | The value is empty for the property `com.ibm.ws.logging.console.source` |
| 11 | Error | The value `0` is not within the valid range `[1..65535]` for the property `default.http.port` |

### `server.env`
| Line | Type | Message |
|------|------|---------|
| 5 | Error | The value `badvalue` is not valid for the variable `WLP_LOGGING_CONSOLE_FORMAT` |
| 8 | Error | The value `Message` is not valid for the variable `WLP_LOGGING_CONSOLE_SOURCE` |
| 11 | Error | The value `99999` is not within the valid range `[1..65535]` for the variable `WLP_DEBUG_ADDRESS` |
| 14 | Warning | The value is empty for the variable `WLP_DEBUG_SUSPEND` |

### `server.xml`
| Line | Type | Message |
|------|------|---------|
| 5 | Error | `servlet-6.X` is not a valid Liberty feature |

See [liberty-testing.md](../../docs/liberty-testing.md) for the full test walkthrough.
