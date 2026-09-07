
# FederatedRecallRequest


## Properties

Name | Type
------------ | -------------
`queryText` | string
`namespaces` | Array&lt;string&gt;
`topK` | number
`perNamespaceTopK` | number
`timeoutMs` | number
`maxColdOpens` | number
`profile` | string
`scoringMode` | string

## Example

```typescript
import type { FederatedRecallRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "queryText": null,
  "namespaces": null,
  "topK": null,
  "perNamespaceTopK": null,
  "timeoutMs": null,
  "maxColdOpens": null,
  "profile": null,
  "scoringMode": null,
} satisfies FederatedRecallRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FederatedRecallRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


