
# AgeDistributionResponse


## Properties

Name | Type
------------ | -------------
`buckets` | [Array&lt;AgeBucketDto&gt;](AgeBucketDto.md)
`totalMemories` | number
`oldestMemory` | string
`newestMemory` | string

## Example

```typescript
import type { AgeDistributionResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "buckets": null,
  "totalMemories": null,
  "oldestMemory": null,
  "newestMemory": null,
} satisfies AgeDistributionResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgeDistributionResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


