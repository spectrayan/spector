
# SessionsResponse


## Properties

Name | Type
------------ | -------------
`sessions` | [Array&lt;SessionSummary&gt;](SessionSummary.md)
`hasMore` | boolean

## Example

```typescript
import type { SessionsResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "sessions": null,
  "hasMore": null,
} satisfies SessionsResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionsResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


