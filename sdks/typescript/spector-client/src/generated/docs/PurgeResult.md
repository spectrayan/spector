
# PurgeResult


## Properties

Name | Type
------------ | -------------
`memoryId` | string
`namespaceId` | string
`found` | boolean
`purgedAt` | Date
`payloadBytesZeroed` | number
`textBytesZeroed` | number
`inlineTextDropped` | boolean
`textRetainedShared` | number
`textSharedWith` | number
`hebbianEdgesRemoved` | number
`temporalUnlinked` | boolean
`entityLinksRemoved` | number
`hyperedgesRemoved` | number
`walRecorded` | boolean
`retainedHeaderFields` | Array&lt;string&gt;
`unreachableCopies` | Array&lt;string&gt;
`disclosure` | string

## Example

```typescript
import type { PurgeResult } from ''

// TODO: Update the object below with actual values
const example = {
  "memoryId": null,
  "namespaceId": null,
  "found": null,
  "purgedAt": null,
  "payloadBytesZeroed": null,
  "textBytesZeroed": null,
  "inlineTextDropped": null,
  "textRetainedShared": null,
  "textSharedWith": null,
  "hebbianEdgesRemoved": null,
  "temporalUnlinked": null,
  "entityLinksRemoved": null,
  "hyperedgesRemoved": null,
  "walRecorded": null,
  "retainedHeaderFields": null,
  "unreachableCopies": null,
  "disclosure": null,
} satisfies PurgeResult

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PurgeResult
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


