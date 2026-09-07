
# MemoryStatusResponse


## Properties

Name | Type
------------ | -------------
`totalMemories` | number
`tierCounts` | { [key: string]: number; }
`hebbianEdges` | number
`temporalLinks` | number
`entityNodes` | number
`entityEdges` | number

## Example

```typescript
import type { MemoryStatusResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "totalMemories": null,
  "tierCounts": null,
  "hebbianEdges": null,
  "temporalLinks": null,
  "entityNodes": null,
  "entityEdges": null,
} satisfies MemoryStatusResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MemoryStatusResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


