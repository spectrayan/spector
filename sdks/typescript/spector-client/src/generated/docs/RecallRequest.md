
# RecallRequest


## Properties

Name | Type
------------ | -------------
`query` | string
`topK` | number
`depth` | number
`tags` | Array&lt;string&gt;
`scoringMode` | string
`recallMode` | string

## Example

```typescript
import type { RecallRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "query": null,
  "topK": null,
  "depth": null,
  "tags": null,
  "scoringMode": null,
  "recallMode": null,
} satisfies RecallRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecallRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


