
# GraphNodeDto


## Properties

Name | Type
------------ | -------------
`id` | string
`tier` | string
`textPreview` | string
`importance` | number
`valence` | number
`timestampMs` | number
`entityNames` | Array&lt;string&gt;

## Example

```typescript
import type { GraphNodeDto } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "tier": null,
  "textPreview": null,
  "importance": null,
  "valence": null,
  "timestampMs": null,
  "entityNames": null,
} satisfies GraphNodeDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GraphNodeDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


