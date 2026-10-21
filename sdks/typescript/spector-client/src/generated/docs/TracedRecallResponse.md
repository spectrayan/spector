
# TracedRecallResponse


## Properties

Name | Type
------------ | -------------
`query` | string
`results` | [Array&lt;TracedRecallItemDto&gt;](TracedRecallItemDto.md)
`totalResults` | number
`latencyMicros` | number
`traceEnabled` | boolean

## Example

```typescript
import type { TracedRecallResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "query": null,
  "results": null,
  "totalResults": null,
  "latencyMicros": null,
  "traceEnabled": null,
} satisfies TracedRecallResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TracedRecallResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


