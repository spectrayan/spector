
# TracedRecallItemDto


## Properties

Name | Type
------------ | -------------
`id` | string
`score` | number
`memoryType` | string
`importance` | number
`ageDays` | number
`recallCount` | number
`valence` | string
`retrievalMode` | string
`breakdown` | [ScoreBreakdownDto](ScoreBreakdownDto.md)

## Example

```typescript
import type { TracedRecallItemDto } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "score": null,
  "memoryType": null,
  "importance": null,
  "ageDays": null,
  "recallCount": null,
  "valence": null,
  "retrievalMode": null,
  "breakdown": null,
} satisfies TracedRecallItemDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TracedRecallItemDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


