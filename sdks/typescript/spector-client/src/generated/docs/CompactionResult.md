
# CompactionResult


## Properties

Name | Type
------------ | -------------
`tier` | string
`beforeCount` | number
`afterCount` | number
`tombstonesRemoved` | number
`bytesReclaimed` | number
`durationMs` | number

## Example

```typescript
import type { CompactionResult } from ''

// TODO: Update the object below with actual values
const example = {
  "tier": null,
  "beforeCount": null,
  "afterCount": null,
  "tombstonesRemoved": null,
  "bytesReclaimed": null,
  "durationMs": null,
} satisfies CompactionResult

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CompactionResult
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


