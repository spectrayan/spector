
# EnrichmentStatusResponse


## Properties

Name | Type
------------ | -------------
`totalMemories` | number
`enrichedMemories` | number
`pendingMemories` | number
`totalEntitiesAdded` | number
`totalRelationsAdded` | number
`inProgress` | boolean
`lastRunDurationMs` | number
`lastError` | string

## Example

```typescript
import type { EnrichmentStatusResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "totalMemories": null,
  "enrichedMemories": null,
  "pendingMemories": null,
  "totalEntitiesAdded": null,
  "totalRelationsAdded": null,
  "inProgress": null,
  "lastRunDurationMs": null,
  "lastError": null,
} satisfies EnrichmentStatusResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EnrichmentStatusResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


