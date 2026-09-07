
# MemoryTableRow


## Properties

Name | Type
------------ | -------------
`id` | string
`text` | string
`textPreview` | string
`tier` | string
`source` | string
`importance` | number
`valence` | number
`arousal` | number
`timestampMs` | number
`agentRecallCount` | number
`recallCount` | number
`tombstoned` | boolean
`suppressed` | boolean
`pinned` | boolean
`resolved` | boolean
`consolidated` | boolean
`tags` | Array&lt;string&gt;
`synapticTags` | number
`l2Norm` | number
`createdAt` | string
`metadata` | { [key: string]: string; }

## Example

```typescript
import type { MemoryTableRow } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "text": null,
  "textPreview": null,
  "tier": null,
  "source": null,
  "importance": null,
  "valence": null,
  "arousal": null,
  "timestampMs": null,
  "agentRecallCount": null,
  "recallCount": null,
  "tombstoned": null,
  "suppressed": null,
  "pinned": null,
  "resolved": null,
  "consolidated": null,
  "tags": null,
  "synapticTags": null,
  "l2Norm": null,
  "createdAt": null,
  "metadata": null,
} satisfies MemoryTableRow

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MemoryTableRow
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


