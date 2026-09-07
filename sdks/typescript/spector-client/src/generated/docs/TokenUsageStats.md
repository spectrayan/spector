
# TokenUsageStats


## Properties

Name | Type
------------ | -------------
`inputTokens` | number
`outputTokens` | number
`embeddingTokens` | number
`totalTokens` | number
`requestCount` | number
`firstRecorded` | Date
`lastRecorded` | Date

## Example

```typescript
import type { TokenUsageStats } from ''

// TODO: Update the object below with actual values
const example = {
  "inputTokens": null,
  "outputTokens": null,
  "embeddingTokens": null,
  "totalTokens": null,
  "requestCount": null,
  "firstRecorded": null,
  "lastRecorded": null,
} satisfies TokenUsageStats

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TokenUsageStats
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


