
# RecallResponse


## Properties

Name | Type
------------ | -------------
`results` | [Array&lt;RecallResult&gt;](RecallResult.md)
`truncated` | boolean
`partitionsVisited` | number
`totalMemories` | number

## Example

```typescript
import type { RecallResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "results": null,
  "truncated": null,
  "partitionsVisited": null,
  "totalMemories": null,
} satisfies RecallResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecallResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


