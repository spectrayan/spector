
# FederatedRecallResponse


## Properties

Name | Type
------------ | -------------
`hits` | [Array&lt;FederatedRecallHit&gt;](FederatedRecallHit.md)
`summary` | [FederatedRecallSummary](FederatedRecallSummary.md)

## Example

```typescript
import type { FederatedRecallResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "hits": null,
  "summary": null,
} satisfies FederatedRecallResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FederatedRecallResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


