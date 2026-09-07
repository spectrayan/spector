
# FederatedRecallSummary


## Properties

Name | Type
------------ | -------------
`queriedCount` | number
`openedNamespaces` | Array&lt;string&gt;
`skippedColdNamespaces` | Array&lt;string&gt;
`deniedNamespaces` | Array&lt;string&gt;
`failedNamespaces` | Array&lt;string&gt;
`executionDurationMs` | number

## Example

```typescript
import type { FederatedRecallSummary } from ''

// TODO: Update the object below with actual values
const example = {
  "queriedCount": null,
  "openedNamespaces": null,
  "skippedColdNamespaces": null,
  "deniedNamespaces": null,
  "failedNamespaces": null,
  "executionDurationMs": null,
} satisfies FederatedRecallSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FederatedRecallSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


