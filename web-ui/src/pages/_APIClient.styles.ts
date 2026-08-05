import { utils } from '@inductiveautomation/ignition-web-ui';

const { makeStyles, getFontStyles } = utils;

export const getAPIClientPageStyles = makeStyles()((theme: any) => {
  const pxToRem = theme.typography.pxToRem;

  return {
    acTableContainer: {
      margin: '1.5rem 1.56rem',
    },
    acBlankStateContainer: {
      display: 'flex',
      justifyContent: 'center',
      marginTop: '6rem',
    },
    acFontRed: {
      color: 'red',
    },
    acDrawerCard: {
      marginBottom: '1rem',
    },
    acForm: {
      padding: '1rem',
    },
    acFormRoot: {
      display: 'flex',
      flexDirection: 'column',
      'div[class*="-formInput"]:not([class*="-acFormCol"])': {
        '&:not(:first-of-type)': {
          marginTop: '1.5rem',
        },
        '&[data-indent="true"]': {
          marginTop: '0.5rem',
        },
      },
    },
    acDeleteButton: {
      color: theme.palette.error.mainVar,
    },
    // For raw (non-FormControlInput) element groups - e.g. APIOAuth2Drawer's ad-hoc
    // TextInput/Button combos - that don't get acFormRoot's automatic spacing.
    acStack: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      gap: '1rem',
    },
    acModalTitle: {
      display: 'flex',
      justifyContent: 'center',
      position: 'relative',
      height: pxToRem(56),
      alignItems: 'center',
      borderBottom: `${pxToRem(1)} solid ${theme.palette.neutral[20]}`,

      '.title': {
        margin: 0,
        ...getFontStyles(theme, 'h1Medium'),
      },

      button: {
        all: 'unset',
        position: 'absolute',
        right: 0,
        marginRight: pxToRem(20),
        display: 'flex',
        justifyContent: 'center',
        cursor: 'pointer',

        svg: {
          fontSize: pxToRem(24),
          color: theme.palette.primary.mainVar,
        },
      },
    },
    acModal: {
      display: 'flex',
      flexDirection: 'column',
    },
    acModalFooter: {
      display: 'flex',
      flexDirection: 'row',
      justifyContent: 'space-between',
      padding: '1rem',
      borderTop: `1px solid ${theme.palette.neutral[20]}`,
    },
  };
});
