
# ChatConfig


## Properties

Name | Type
------------ | -------------
`defaultModel` | string
`maxContextDepth` | number
`defaultContextDepth` | number
`agentMode` | boolean
`version` | string

## Example

```typescript
import type { ChatConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "defaultModel": null,
  "maxContextDepth": null,
  "defaultContextDepth": null,
  "agentMode": null,
  "version": null,
} satisfies ChatConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ChatConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


