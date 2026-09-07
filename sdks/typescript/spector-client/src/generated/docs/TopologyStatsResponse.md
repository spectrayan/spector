
# TopologyStatsResponse


## Properties

Name | Type
------------ | -------------
`entityTypes` | [Array&lt;EntityTypeStatsDto&gt;](EntityTypeStatsDto.md)
`relationTypes` | [Array&lt;RelationTypeStatsDto&gt;](RelationTypeStatsDto.md)

## Example

```typescript
import type { TopologyStatsResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "entityTypes": null,
  "relationTypes": null,
} satisfies TopologyStatsResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TopologyStatsResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


