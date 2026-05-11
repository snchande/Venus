"""Fix UTF-8 mojibake in Venus Notebooks tutorial files.

The character — (em dash, U+2014) was double-encoded.
UTF-8 bytes E2 80 94 were read as Latin-1 chars U+00E2 U+0080 U+0094,
then each was UTF-8 encoded again, producing:
  C3 A2  = U+00E2 = a with circumflex
  E2 82 AC = U+20AC = euro sign
  E2 80 9D = U+201D = right double quotation mark
Total bad sequence: C3 A2  E2 82 AC  E2 80 9D  -> should be E2 80 94
"""
import os

files = ['java-101', 'java-202', 'java-302']
base  = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'notebooks')

# The bad sequence: C3A2 E282AC E2809D (mojibake for em dash U+2014)
bad_em_dash = b'\xc3\xa2\xe2\x82\xac\xe2\x80\x9d'
good_em_dash = b'\xe2\x80\x94'

# The bad sequence for right single quote U+2019 (apostrophe mojibake)
bad_rsq = b'\xc3\xa2\xe2\x82\xac\xe2\x84\xa2'
good_rsq = b'\xe2\x80\x99'

for name in files:
    path = os.path.join(base, name + '.vnb')
    with open(path, 'rb') as f:
        data = f.read()
    original_len = len(data)
    c1 = data.count(bad_em_dash)
    c2 = data.count(bad_rsq)
    data = data.replace(bad_em_dash, good_em_dash)
    data = data.replace(bad_rsq, good_rsq)
    with open(path, 'wb') as f:
        f.write(data)
    print('Fixed %s: em-dash x%d, apostrophe x%d' % (name, c1, c2))

print('Done.')
