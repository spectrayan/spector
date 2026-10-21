
# ToolCardView


## Properties

Name | Type
------------ | -------------
`callId` | string
`name` | string
`arguments` | { [key: string]: any | null; }
`status` | string
`preview` | string
`truncated` | boolean
`elapsedMs` | number

## Example

```typescript
import type { ToolCardView } from ''

// TODO: Update the object below with actual values
const example = {
  "callId": null,
  "name": null,
  "arguments": null,
  "status": null,
  "preview": null,
  "truncated": null,
  "elapsedMs": null,
} satisfies ToolCardView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ToolCardView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


