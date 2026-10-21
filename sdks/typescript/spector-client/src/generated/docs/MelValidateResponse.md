
# MelValidateResponse


## Properties

Name | Type
------------ | -------------
`valid` | boolean
`statementCount` | number
`statements` | [Array&lt;MelStatementSummary&gt;](MelStatementSummary.md)
`diagnostics` | [Array&lt;MelDiagnostic&gt;](MelDiagnostic.md)

## Example

```typescript
import type { MelValidateResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "valid": null,
  "statementCount": null,
  "statements": null,
  "diagnostics": null,
} satisfies MelValidateResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MelValidateResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


